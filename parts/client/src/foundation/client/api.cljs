(ns foundation.client.api
  (:require [foundation.client.events :as events]
            [foundation.client.forms :as forms]
            [foundation.client.history :as history]
            [foundation.client.logging :as log]
            [foundation.client.default :as default]
            [foundation.client.connection :as connection]
            [foundation.client.config :as config]
            [datascript.core :as ds]
            [replicant.dom :as r]
            [nexus.registry :as nxr])
  (:require-macros [foundation.client.api]))

(nxr/register-system->state! deref)
(nxr/register-placeholder! :event.target/value #(some-> % :replicant/dom-event .-target .-value))
;; after replicant-kanban actions.cljc
(nxr/register-placeholder! :event/form-data (fn [{:replicant/keys [^js dom-event] :as arg}]
                                              (some-> dom-event .-target forms/gather-form-data)))
(def register-effect! nxr/register-effect!)
(register-effect! ::db #(ds/transact! %2 %3))
(register-effect! ::post connection/post!) ; TODO 2026-06-29 14:25:40 think about dispatch beyond just receive
(register-effect! ::navigate history/navigate!)
(register-effect! ::log (fn [_ _ msg] (log/info msg)))
(register-effect! ::prevent-default (fn [{{:replicant/keys [:dom-event]} :dispatch-data} _]
                                      (some-> dom-event .preventDefault)))
(def q ds/q)
