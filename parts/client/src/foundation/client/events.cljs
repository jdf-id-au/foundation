(ns foundation.client.events
  (:require [foundation.client.logging :as log]))

(defn navigate [h rp])

(defn value-as
  "Convert value from string to cljs type. Use `as` instead to handle js event."
  [t v]
  (condp = t
    keyword (keyword v)
    int (let [i (js/parseInt v)] (when-not (js/Number.isNaN i) i))
    float (let [f (js/parseFloat v)] (when-not (js/Number.isNaN f) f))
    str v))

(defn as
  "Convert value from js event target to cljs type."
  [t e]
  (value-as t (-> e .-target .-value)))
