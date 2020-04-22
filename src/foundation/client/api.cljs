(ns foundation.client.api
  (:require [foundation.client.state :as state]
            [foundation.client.events :as events]
            [foundation.client.history :as history]
            [foundation.client.logging :as log]
            [foundation.client.default :as default]
            ["react-dom" :refer [render]]
            [helix.core :refer [$]]
            [foundation.client.config :as config])
  (:require-macros [foundation.client.api]))

(defn store!
  ([schema] (store! schema []))
  ([schema tx-data] (state/store! (merge default/schema schema)
                                  (concat default/tx-data tx-data))))

(def register state/register)
(def subscribe state/subscribe)

(defn start!
  [{:keys [root-component mount-point coeffects effects routes]
    :or {mount-point "app"}}]
  (events/setup! (merge default/coeffects coeffects) (merge default/effects effects))
  (run! #(apply state/register %) default/subscriptions)
  (history/setup! (or routes default/routes))
  (state/listen!)
  (history/listen!)
  (log/info "version" (:version config/config))
  (render ($ root-component) (. js/document getElementById mount-point)))