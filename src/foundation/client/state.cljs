(ns foundation.client.state
  "Connect datascript store to React state."
  (:require [helix.core :refer [$]]
            [helix.hooks :as hooks]
            ["react-dom" :refer [render]]
            [datascript.core :as datascript]
            [foundation.client.logging :as log])
  (:require-macros [foundation.client.state]))

; Subscriptions

(defonce store (atom (datascript/create-conn))) ; adds meta :listeners
(defonce subscriptions (atom {}))
(defonce setters (atom {}))

(defn store!
  "Set up datascript store."
  ([schema] (store! schema []))
  ([schema tx-data] (reset! store (-> (datascript/empty-db schema)
                                      (datascript/db-with tx-data)))))

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

(defn listen!
  "Connect store to React."
  [store]
  (datascript/listen! store :subs watcher))

; Events

(defonce cofx (atom {})) ; {:name [function & args]}
(defonce fx (atom {:back #(.back js/window.history)})) ; {:name function}

(defn retrieve-coeffect [name]
  (or (get @cofx name)
      (and (sub-exists? name) [(partial run-sub name)])
      (log/error "No such coeffect" name)))

(defn do!
  "Allows event-fn to be pure by specifying coeffects (inputs),
   then executing the returned effect descriptions (outputs).

   Each coeffect can be a kw, or a vector containing kw then c-args.

   This kw can be a subscription nskw, or a kw corresponding to a [fn & f-args] vector
   from the `cofx` map atom.

   The coeffect subscription or fn is called with (concatenated) f-args and c-args.

   Coeffects can't currently depend on each other.

   Event-fn is called with (concatenated) coeffect return values, then args.

   Effect descriptions are vectors containing [kw & e-args]. The corresponding fn from the
   `fx` map atom is called with e-args. :no-op can be used for clarity if no effect is required."
  [name event-fn coeffects args]
  (log/debug "Firing" name "with" coeffects "and" args)
  (let [cvalues (into {} (for [coeffect coeffects
                               :let [[cname & cargs] (if (coll? coeffect) coeffect [coeffect])
                                     [cfn & fargs] (retrieve-coeffect cname)]]
                           [coeffect (apply cfn (concat fargs cargs))]))]
    (doseq [[ename & eargs] (apply event-fn (concat (map #(get cvalues %) coeffects) args))
            :when (not= ename :no-op)]
      (if-let [effect-fn (get @fx ename)]
        (do (log/info "Firing effect-fn" ename eargs)
            (try (apply effect-fn eargs)
                 (catch :default e
                   (log/error "Error executing effect" ename "with" args ":" e))))
        (if ename
          (log/error "No such effect-fn" ename eargs fx)
          (log/error "Nil effect-fn" ename eargs fx))))))

; Startup

(defn start!
  [coeffects effects root-component mount-point]
  (reset! cofx coeffects)
  (reset! fx effects)
  (render ($ root-component) (. js/document getElementById mount-point)))