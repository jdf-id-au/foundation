(ns foundation.client.default
  (:require [foundation.client.history :as history]
            [foundation.client.config :as config]
            [foundation.client.logging :as log]
            [foundation.client.connection :as connection]
            [foundation.message :as fm]
            [foundation.db :as fd]
            [oops.core :refer [oget oset!]]))

(def routes
  "Associate navigation hash with routes." ; bidi doesn't like "#" at root
  ["" [["/" ; This map is accessed by history/routed-views:
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

(def tx-data (vec (for [ns (keys state-locations)] {:app/state ns})))

(def subscriptions ; TODO 2026-07-01 20:35:34 adapt to new arch
  "Define some singleton storage value subscriptions, e.g. :ui/view (see `schema`)."
  (for [[n ks] state-locations k ks] (keyword n k)))
