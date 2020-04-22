(ns foundation.client.state
  "Connect datascript store to React state."
  (:require [helix.hooks :as hooks]
            [datascript.core :as datascript]
            [foundation.client.logging :as log]))

(defonce store (datascript/create-conn)) ; adds meta :listeners
(defonce subscriptions (atom {}))
(defonce setters (atom {}))

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

(defn answer
  "Run query against given datascript store, with args if supplied (for `:in` clause).
   Post-process with supplied function, which *also* receives args."
  [store query process args]
  (if (and store query process)
    (apply process (apply datascript/q query store args) args)
    (log/error "Dropped query" {:store? (boolean store) :query query
                                :process? (fn? process) :args args})))

(defn subscribe
  "Wire up React set-state! for given subscription.
   args are passed to the subscription's query *and* its post-processing function."
  [name & args]
  (let [[query process] (name @subscriptions)
        state (answer @store query process args)
        [_ set-state!] (hooks/use-state state)]
    (hooks/use-effect :always ; NB not specifying React deps (macro wants literal vector, not `args`)
      ; FIXME too much remember/forget churn?
      (log/debug "Remembering setter for" name  "with args" args)
      (swap! setters assoc-in [name args] set-state!)
      (fn clean-up []
        (log/debug "Forgetting setter for" name "with args" args)
        (swap! setters update name dissoc args)))
    state))

(defn sub-exists? [name]
  (contains? @subscriptions name))

(defn run-sub
  "Run subscription for use in coeffect."
  [name & args]
  (let [[query process] (name @subscriptions)]
    (answer @store query process args)))

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