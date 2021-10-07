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
  [schema tx-data]
  (if (= @store (datascript/empty-db))
    (reset! store (-> (datascript/empty-db schema)
                      (datascript/db-with tx-data)))
    (log/warn "Store not empty so not changed.")))

(defn register
  "Register a subscription, being a query (see `answer` for types) and optional post-processing function."
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
     (register nskw [:find '?v '. :where [[:app/state (keyword ns)] (keyword n) '?v]] process))))

(defn singleton
  "Convenience for creating datom for singleton storage value (see f.client.default/schema).
   Returns `{:app/state kw-namespace kw-name value}`."
  [nskw value]
  (let [ns (namespace nskw) n (name nskw)]
    {:app/state (keyword ns) (keyword n) value}))

(defn- run-sub-impl
  "Look up subscription and run it."
  [store answer-fn name & args]
  #_(log/debug "Trying to run sub" name args (name @subscriptions))
  (if-let [[query process] (name @subscriptions)]
    (answer-fn store query process args)
    (log/error "No such subscription" name)))

(defn answer ; TODO profile?
  "Find value of subscription, by:
    - running query against given datascript store (args bound by `:in` clause), or
      query e.g. `'[:find ...]`
    - running index lookup against given datascript store (args applied to `d/datoms` call), or
      query e.g. `[:aevt :attribute-name]`
    - referring to the value of other subscriptions.
      query e.g. `{:sub-name [sub-args ...]}`
   Post-process with supplied function, which *also* receives args."
  [store query process args]
  #_(log/debug "answering query" query "args" args "with" process "store" (boolean store))
  (if (and store query process)
    (let [q (case (first query)
              :find (apply datascript/q query store args)
              (:eavt :aevt :avet) (apply datascript/datoms store (concat query args))
              (if (map? query)
                (into {}
                  ; TODO method for passing args through from calling sub?
                  ; TODO cache subquery results within a given run to prevent reexecution (shouldn't need to include actual store)
                  (map (fn [[sub-name sub-args]]
                         [sub-name (apply run-sub-impl store answer sub-name sub-args)]))
                  query)
                ::unsupported))] ; allow nil return value from queries
      (case q ::unsupported (do (log/error "Unsupported query" query)
                                (throw (js/Error. "Unsupported query")))
        (apply process q args)))
    (log/error "Dropped query" {:store? (boolean store) :query query
                                :process? (fn? process) :args args})))

(defn sub-exists? [name] (contains? @subscriptions name))

(defn subscribe
  "Wire up React set-state! for given subscription.
   args are passed to the subscription's query *and* its post-processing function."
  [name & args]
  (assert (sub-exists? name) (str "No such subscription " name))
  (let [[query process] (name @subscriptions)
        state (answer @store query process args)
        [_ set-state!] (hooks/use-state state)]
    (hooks/use-effect :auto-deps ; TODO contemplate :always vs :auto-deps
      ; FIXME too much remember/forget churn?
      #_(log/debug "Remembering setter for" name "with args" args)
      (swap! setters update name update args #(into #{set-state!} %))
      (fn clean-up []
        #_(log/debug "Forgetting setter for" name "with args" args)
        (swap! setters update name update args disj set-state!)))
    state))

(defn run-sub
  "Run subscription for use in coeffect."
  [name & args] ; has to be defn because otherwise derefs store prematurely!
  (apply run-sub-impl @store answer name args))

(defn watcher
  "Fire subscriptions when datascript store changes."
  [{:keys [tx-meta db-after] :as tx-report}]
  ; TODO be more selective (if there's a performance problem), haven't decided how
  (doseq [[name [query process]] @subscriptions
          #_#__ (log/debug "watched" name query)
          [args ss] (name @setters)
          :let [result (answer db-after query process args)]
          setter ss]
    (setter result)))

(def transact! (partial datascript/transact! store))
(defn listen! [] (datascript/listen! store :subs watcher))

(defn hot-text
  "Avoid spamming datascript with per-character text input.
   `submit` is event-fn to fire with completed text.
   onChange is for input, onSubmit is for form."
  [initial submit]
  (let [[state set-state] (hooks/use-state initial)
        onChange (fn [e] (-> e .-target .-value str set-state))
        onSubmit (fn [e] (submit state) (set-state initial) (.preventDefault e))]
    [state onChange onSubmit]))