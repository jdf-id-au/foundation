(ns foundation.client.api
  (:require [foundation.client.state :as state]
            [foundation.client.events :as events]
            [foundation.client.history :as history]
            [foundation.client.logging :as log]
            [foundation.client.default :as default]
            ["react-dom/client" :refer [createRoot]]
            [helix.core :refer [$]]
            [foundation.client.config :as config])
  (:require-macros [foundation.client.api])) ; allows f/defevent when this ns aliased as f

(defn store!
  ([schema] (store! schema []))
  ([schema tx-data]
   #_(log/debug "setting up store with" (merge default/schema schema))
   (state/store! (merge default/schema schema)
                 (concat default/tx-data tx-data))))

(def register state/register)
(def register-singleton state/register-singleton)
(def subscribe state/subscribe)

(defn start!
  "Setup events, subscriptions, history and state, then render root component."
  [{:keys [root-component mount-point coeffects effects routes]
    :or {mount-point "app"}}]
  (events/setup! (merge default/coeffects coeffects) (merge default/effects effects))
  (run! state/register-singleton default/subscriptions)
  (history/setup! (or routes default/routes))
  (state/listen!)
  (history/listen!)
  (log/info "version" (:version config/config))
  (let [root (createRoot (. js/document getElementById mount-point))]
    (.render root ($ root-component))))

(def value-as events/value-as)
(def as events/as)
(def hot-text state/hot-text)
