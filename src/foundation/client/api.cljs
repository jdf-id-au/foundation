(ns foundation.client.api
  (:require [foundation.client.state :as state]
            [foundation.client.events :as events]
            ["react-dom" :refer [render]]
            [helix.core :refer [$]])
  (:require-macros [foundation.client.api]))

(def store! state/store!)
(def register state/register)
(def subscribe state/subscribe)

(defn start!
  ([root-component] (start! root-component "app" {} {}))
  ([root-component mount-point coeffects effects]
   (events/redefine! coeffects effects)
   (state/listen!)
   (render ($ root-component) (. js/document getElementById mount-point))))