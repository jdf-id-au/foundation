(ns foundation.client.api
  (:require [foundation.client.events :as events]
            [foundation.client.history :as history]
            [foundation.client.logging :as log]
            [foundation.client.default :as default]
            [foundation.client.connection :as connection]
            [foundation.client.config :as config]
            [datascript.core :as ds]
            [replicant.dom :as r]
            [nexus.registry :as nxr])
  )

(nxr/register-system->state! deref)
(nxr/register-effect! ::db #(ds/transact! %2 %3))
(nxr/register-effect! ::post connection/post!) ; TODO 2026-06-29 14:25:40 think about dispatch beyond just receive
(nxr/register-effect! ::navigate history/navigate!)

(defonce render* (atom #(log/warn "Renderer not initialised")))
(defonce conn* (atom nil)) ; will contain ds conn, itself derefable to give value

(defn init [gen-hiccup & {:keys [schema tx-data routes element]
                        :or {element "app"}}]
  (let [conn (reset! conn* (ds/create-conn (merge default/schema schema))) ; known as "system" in nexus: "a mutable application object"
        element (js/document.getElementById element)
        dispatch (partial nxr/dispatch conn)]
    (r/set-dispatch! dispatch)
    (history/setup! routes)
    (history/listen! dispatch)
    (add-watch conn ::render
      (fn [_ _ _ _]
        ((reset! render*
           #(r/render element (gen-hiccup @conn))))))))

(defn ^:dev/after-load render [] (@render*))
(defn conn [] @conn*)
