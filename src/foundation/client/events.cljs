(ns foundation.client.events
  (:require [foundation.client.state :as state]
            [foundation.client.logging :as log])
  (:require-macros [foundation.client.api :refer [defevent]]))

(defonce cofx (atom {}))
(defonce fx (atom {}))

(defn setup! [coeffects effects]
  (reset! cofx coeffects)
  (reset! fx effects))

(defn retrieve-coeffect [name]
  (or (get @cofx name)
      (and (state/sub-exists? name) [(partial state/run-sub name)])
      (log/throw "No such coeffect" name)))

(defn do!
  "Allows event-fn to be pure by specifying coeffects (inputs),
   then executing the returned effect descriptions (outputs).

   Each coeffect can be a kw, or a vector containing kw then c-args.

   This kw can be a subscription nskw, or a kw corresponding to a [fn & f-args] vector
   from the `cofx` map atom.

   The coeffect subscription or fn is called with (concatenated) f-args and c-args.

   Coeffects can't currently depend on each other.

   Event-fn is called with (concatenated) coeffect return values, then args. It returns either
   one effect description, or a coll of them.

   Effect descriptions are vectors containing [kw & e-args]. The corresponding fn from the
   `fx` map atom is called with e-args. :no-op can be used for clarity if no effect is required."
  [name event-fn coeffects args]
  (log/debug "Firing" name "with" coeffects "and" args)
  (let [cvalues (into {} (for [coeffect coeffects
                               :let [[cname & cargs] (if (coll? coeffect) coeffect [coeffect])
                                     [cfn & fargs] (retrieve-coeffect cname)]]
                           [coeffect (apply cfn (concat fargs cargs))]))
        effects (apply event-fn (concat (map #(get cvalues %) coeffects) args))
        ; Wrap single effect description:
        effects (cond-> effects (-> effects first keyword?) vector)]
    (doseq [[ename & eargs] effects
            :when (not= ename :no-op)]
      (if-let [effect-fn (get @fx ename)]
        (do (log/info "Firing effect-fn" ename eargs)
            (try (apply effect-fn eargs)
                 (catch :default e
                   (log/throw "Error executing effect" ename "with" args ":" e))))
        (if ename
          (log/throw "No such effect" ename eargs fx)
          (log/throw "Nil effect" ename eargs fx))))))

(defevent navigate
  (fn -navigate [current-view current-rps view route-params]
    (let [rps (into {} (map (juxt first (comp str second)) route-params))]
      (if-not (and (= view current-view)
                   (= rps current-rps))
        (letfn [(go [v r] (do (log/debug "Purely going" v r)
                              [[:db [{:app/state :ui :view v :route-params r}]]
                               [:navigate v r]]))
                (navigate [] (go view rps))
                (not-found [] (go :not-found nil))]
          (navigate)
          ; TODO this is where authorization etc could be checked
          ; ...would need relevant coeffects and therefore separate implementation
          ; ...and therefore not have this implementation hardwired into f.c.history/navigated
          #_(case view
              (:not-found :home) (navigate))))))
              ; else
  :ui/view :ui/route-params)