(ns foundation.client.api
  (:require 
            [foundation.client.events :as events]
            [foundation.client.history :as history]
            [foundation.client.logging :as log]
            [foundation.client.default :as default]
            [foundation.client.config :as config])
  )
(comment
  (defn store!
    ([schema] (store! schema []))
    ([schema tx-data]
     #_(log/debug "setting up store with" (merge default/schema schema))
     (state/store! (merge default/schema schema)
       (concat default/tx-data tx-data))))
  

  (defn init!
    "Setup events, subscriptions, history, state and root component."
    [{:keys [mount-point coeffects effects routes]
      :or {mount-point "app"}}]
    
    
    (history/setup! (or routes default/routes))
    
    (history/listen!)
    (log/info "version" (:version config/config))
    )

  (def value-as events/value-as)
  (def as events/as)
  (def hot-text state/hot-text))
