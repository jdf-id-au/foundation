(ns foundation.client.api
  (:require [foundation.client.state :as state]
            [foundation.client.events :as events]
            [foundation.client.history :as history]
            [foundation.client.logging :as log]
            [foundation.client.default :as default]
            ["react-dom" :refer [render]]
            [helix.core :refer [$]])
  (:require-macros [foundation.client.api]))

(defn store!
  ([schema] (store! schema []))
  ([schema tx-data] (state/store! (log/show (merge default/schema schema))
                                  (log/show (concat default/tx-data tx-data)))))

(def register state/register)
(def subscribe state/subscribe)

(defn start!
  [{:keys [root-component mount-point coeffects effects routes]
    :or {mount-point "app"}}]
  (events/setup! (merge default/coeffects coeffects) (merge default/effects effects))
  (doseq [[name query] default/subscriptions] (state/register name query))
  (history/setup! (or routes default/routes))
  (state/listen!)
  (history/listen!)
  (render ($ root-component) (. js/document getElementById mount-point)))