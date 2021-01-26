(ns foundation.client.state
  "Connect datascript store to React state."
  (:require [helix.hooks :as hooks]
            [datascript.core :as datascript]
            [foundation.client.logging :as log]))

(defonce store (datascript/create-conn)) ; adds meta :listeners
(defonce subscriptions (atom {}))
(defonce setters (atom {}))

#_(add-watch store :debug (fn [key ref old new] (log/debug "watching store" old "->" new)))

(defn store!
  "Set up datascript store."
  [schema tx-data] (reset! store (-> (datascript/empty-db schema)
                                     (datascript/db-with tx-data))))

(defn register
  "Register a subscription, being a query and optional post-processing function."
  ([name query] (register name query nil))
  ([name query process]
   (swap! subscriptions assoc name
     [query (or process (fn unprocessed [result & args] result))])))

(defn register-singleton
  "Convenience for registering singleton storage value subscriptions (see f.client.default/schema)."
  ([nskw] (register-singleton nskw nil))
  ([nskw process]
   (let [ns (namespace nskw) n (name nskw)]
     ; NB could consider `[:eavt [:app/state ns] n] (comp :v first)` if slow
     (register (keyword ns n) [:find '?v '. :where [[:app/state (keyword ns)] (keyword n) '?v]] process))))

(defn- run-sub-impl
  "Look up subscription and run it."
  [store answer-fn name & args]
  (if-let [[query process] (name @subscriptions)]
    (answer-fn store query process args)
    (log/error "No such subscription" name)))

(defn answer ; TODO memoise? profile?
  "Find value of subscription, by:
    - running query against given datascript store (args bound by `:in` clause), or
      query e.g. `'[:find ...]`
    - running index lookup against given datascript store (args applied to `d/datoms` call), or
      query e.g. `[:aevt :attribute-name]`
    - referring to the value of other subscriptions.
      query e.g. `{:sub-name [sub-args ...]}`
   Post-process with supplied function, which *also* receives args."
  [store query process args]
  #_(log/debug "answering query" query "args" args "against" store)
  (if (and store query process)
    (let [q (case (first query)
              :find (apply datascript/q query store args)
              (:eavt :aevt :avet) (apply datascript/datoms store (concat query args))
              (if (map? query)
                (into {}
                  (map (fn [[sub-name sub-args]]
                         [sub-name (apply run-sub-impl store answer sub-name sub-args)]))
                  query)
                ::unsupported))] ; allow nil return value from queries
      (case q ::unsupported (do (log/error "Unsupported query" query)
                                (throw (js/Error. "Unsupported query")))
        (apply process q args)))
    (log/error "Dropped query" {:store? (boolean store) :query query
                                :process? (fn? process) :args args})))

(defn subscribe
  "Wire up React set-state! for given subscription.
   args are passed to the subscription's query *and* its post-processing function."
  [name & args]
  (let [[query process] (name @subscriptions)
        state (answer @store query process args)
        [_ set-state!] (hooks/use-state state)]
    (hooks/use-effect :always
      ; NB not specifying React deps (macro wants literal vector, not `args`)
      ; FIXME too much remember/forget churn?
      #_(log/debug "Remembering setter for" name  "with args" args)
      (swap! setters assoc-in [name args] set-state!)
      (fn clean-up []
        #_(log/debug "Forgetting setter for" name "with args" args)
        (swap! setters update name dissoc args)))
    state))

(defn sub-exists? [name]
  (contains? @subscriptions name))

(defn run-sub
  "Run subscription for use in coeffect."
  [name & args] ; has to be defn because otherwise derefs store prematurely!
  (apply run-sub-impl @store answer name args))

(defn watcher
  "Fire subscriptions when datascript store changes."
  [{:keys [tx-meta db-after] :as tx-report}] ; TODO be more selective, haven't decided how
  (doseq [[name [query process]] @subscriptions
          [args setter] (name @setters)
          :let [result (answer db-after query process args)]]
    (setter result)))

(def transact! (partial datascript/transact! store))

(defn listen! []
  (datascript/listen! store :subs watcher))