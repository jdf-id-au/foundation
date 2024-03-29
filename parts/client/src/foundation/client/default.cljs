(ns foundation.client.default
  (:require [foundation.client.history :as history]
            [foundation.client.state :as state]
            [foundation.client.events :as events]
            [foundation.client.logging :as log]
            [foundation.client.connection :as connection]
            [foundation.db :as fd]))

(def routes
  "Associate navigation tokens (being the part of URL after #) with routes."
  ["" [["/"
        ; This map is accessed by history/routed-views:
        {"" :home
         "not-found" :not-found}] ; needed to make catchall routeable
       ["" :home]
       [true :not-found]]]) ; catchall

; Support "non-databasey" singletons

(def schema
  "Provide singleton storage groups.
   e.g. {:app/state :ui :view :view-name :route-params {}}}
   Allows queries using lookup refs i.e. [:app/state :ui] in place of ?e.
   I figure attribute names can be plain kws because lookup ref is like ns.
   Use attribute name as ns in subscriptions, e.g. :ui/view ." ; NB may change if speccing
  {:app/state fd/primary-key})

(def state-locations
  "Used to create default app state subscriptions."
  {:ui ; user interface
   [:view :route-params]
   :<-> ; communication
   [:username :token]})

(def tx-data
  (for [ns (keys state-locations)]
    {:app/state ns}))

(def subscriptions
  "Define some singleton storage value subscriptions, e.g. :ui/view (see `schema`)."
  (for [[n ks] state-locations k ks] (keyword n k)))

; Support pure event-fns

(def coeffects
  "Map of :name -> [function & args]."
  {})

(def effects
  "Map of :name -> function."
  {:db state/transact!
   :debug log/debug
   :navigate history/navigate!
   :back #(.back js/window.history)
   :restart #(.assign js/window.location (subs (.-href js/window.location) 0 (.indexOf (.-href js/window.location) (.-hash js/window.location))))
   :dispatch events/dispatch!
   :get connection/get!
   :post connection/post!
   ;:auth connection/auth!
   :websocket connection/websocket!
   :send connection/send!})
