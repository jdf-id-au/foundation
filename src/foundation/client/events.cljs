(ns foundation.client.events
  (:require [foundation.client.state :as state]
            [foundation.client.logging :as log]))

(def cofx-defaults
  "Map of :name -> [function & args]."
  {})

(def fx-defaults
  "Map of :name -> function."
  {:db state/transact!
   :debug log/debug
   :back #(.back js/window.history)})

(defonce cofx (atom cofx-defaults))
(defonce fx (atom fx-defaults))

(defn redefine! [coeffects effects]
  (reset! cofx (merge cofx-defaults coeffects))
  (reset! fx (merge fx-defaults effects)))

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
                   (log/throw "Error executing effect" ename "with" args ":" e))))
        (if ename
          (log/throw "No such effect" ename eargs fx)
          (log/throw "Nil effect" ename eargs fx))))))