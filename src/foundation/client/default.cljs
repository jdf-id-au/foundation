(ns foundation.client.default
  (:require [foundation.client.history :as history]
            [foundation.client.state :as state]
            [foundation.client.logging :as log]))

(def routes
  "Associate navigation tokens (being the part of URL after #) with routes."
  ["" [["/"
        ; This map is accessed by routed-views below:
        {"" :home
         "not-found" :not-found}] ; needed to make catchall routeable
       ["" :home]
       [true :not-found]]]) ; catchall

(def schema
  "Provide singleton storage groups.
   e.g. {:app/state :ui :app/view :view-name :app/route-params ...}
   Allows queries using lookup refs i.e. [:app/state :ui] in place of ?e."
  {:app/state {:db/unique :db.unique/identity}}) ; aka primary key

(def tx-data
  [{:app/state :ui}])

(def subscriptions
  {:app/view '[:find ?v . :where [[:app/state :ui] :app/view ?v]]
   :app/route-params '[:find ?v . :where [[:app/state :ui] :app/route-params ?v]]})

(def coeffects
  "Map of :name -> [function & args]."
  {})

(def effects
  "Map of :name -> function."
  {:db state/transact!
   :debug log/debug
   :navigate history/navigate!
   :back #(.back js/window.history)})