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
   e.g.
  {:app/state :ui :view :view-name :route-params {}}}
  {:app/state :auth :user \"user name\" :token \"token\"}
   Allows queries using lookup refs i.e. [:app/state :ui] in place of ?e.
   I figure attribute names can be plain kws because lookup ref is like ns. "
  {:app/state fd/primary-key})

(def state-locations
  {:ui
   [:view :route-params]
   :auth
   [:username :token]})

(def tx-data (vec (for [ns (keys state-locations)] {:app/state ns})))
