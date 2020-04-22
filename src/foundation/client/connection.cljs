(ns foundation.client.connection ; FIXME *** adapt from Leavetracker
  (:import (goog.net WebSocket)
           (goog.net.WebSocket EventType)) ; != (goog.net EventType)
  (:require [goog.events :refer [listen]]
            [foundation.message :as message :refer [->transit <-transit]]
            [foundation.client.config :as config]
            [foundation.client.logging :as log])
  (:require-macros [foundation.client.api :refer [defevent]]))

(def conform (partial message/conform ::message/->client))
(def validate (partial message/conform ::message/->server))

; Communication-specific events

(defevent ws-open
  (fn [#_{:keys [user token]} open?]
    ;[[:send [:auth user token]]] ; TODO
    [[:db [{:app/state :com :app/online true}]]]
    [[:db [{:app/state :com :app/online false}]]]))

(defevent receive
  message/receive)

; Interop

(defonce -websocket
  (let [ws (WebSocket.)]
    (listen ws EventType.OPENED
            (fn [_]
              (log/debug "Websocket open")
              (ws-open true)))
    (listen ws EventType.MESSAGE
            ; NB non-conforming messages would raise error because no corresponding multimethod?
            (fn [event] (-> event .-message <-transit conform receive)))
    (listen ws EventType.ERROR
            (fn [event] ; TODO
              (log/error "Websocket error" (or (.-data event) ""))))
    (listen ws EventType.CLOSED
            (fn [_]
              (log/debug "Websocket closed")
              (ws-open false)))
    ws))

; FIXME make sure ws open (e.g. if server rebooted?)
(defn send! [msg] (.send -websocket (-> msg validate ->transit)))

(defn websocket! [action]
  (try (case action
         :connect (.open -websocket (config/api "ws" "ws"))
         :disconnect (.close -websocket))
       (catch :default e (log/error e))))