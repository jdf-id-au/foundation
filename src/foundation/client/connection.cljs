(ns foundation.client.connection ; FIXME *** adapt from Leavetracker
  (:require [goog.events :refer [listen]]
            [goog.crypt.base64 :as b64]
            [ajax.core :as ajax]
            [foundation.message :as message :refer [->transit <-transit]]
            [foundation.client.config :as config]
            [foundation.client.logging :as log]
            [foundation.client.events]) ; think this is needed for defevent macro
  (:require-macros [foundation.client.api :refer [defevent]])
  (:import (goog.net WebSocket)
           (goog.net.WebSocket EventType))) ; != (goog.net EventType)

; Websocket - validated on both sides with clojure.spec

(def conform (partial message/conform ::message/->client))
(def validate (partial message/conform ::message/->server))

(defevent ws-open
  (fn [#_{:keys [user token]} open?]
    ;[[:send [:auth user token]]] ; TODO
    [[:db [{:app/state :<-> :online true}]]]
    [[:db [{:app/state :<-> :online false}]]]))

(defevent receive message/receive)

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
(defn send!
  "Send over websocket."
  [msg] (.send -websocket (-> msg validate ->transit)))

(defn websocket!
  "Connect or disconnect websocket."
  [action]
  (try (case action
         :connect (.open -websocket (config/api "ws" "ws"))
         :disconnect (.close -websocket))
       (catch :default e (log/error e))))

; Ajax ; TODO plug into similar validation system as ws (plus endpoint validation?)

(defn get!
  "Ajax query"
  [endpoint params handler failer]
  (ajax/GET (config/api endpoint)
            {:timeout (:timeout config/config)
             :handler handler ; TODO handler needs to validate received message
             :error-handler failer
             :params params}))

(defn post!
  "Ajax command"
  [endpoint message handler failer]
  (ajax/POST (config/api endpoint)
             {:timeout (:timeout config/config)
              :handler handler
              :error-handler failer
              :body message ; TODO message needs to be validated first
              :format :transit}))

#_(defn delete!
    "Ajax command"
    [endpoint handler failer]
    (ajax/DELETE (config/api endpoint)
                 {:timeout (:timeout config/config)
                  :handler handler
                  :error-handler failer}))

; Auth

(defevent auth-failer ; NB example only
  (fn [{:keys [status] :as response}]
    (log/info "Auth failure" response)
    (case status
      ; wrong credentials:
      #_{:status 401 :status-text "Unauthorized." :failure :error
         :response {:cause "No authorization provided"
                    :data {:status 401}
                    :headers {"www-authenticate" ["Basic realm=\"default\""]}}}
      401 [[:db [{:app/state :<-> :auth :fail}]]]
      ; server not running:
      #_{:status 0 :status-text "Request failed." :failure :failed}
      [[:db [{:app/state :<-> :auth :error}]]])))

(defevent auth-handler
  (fn [{:keys [username token]}]
    [[:db [{:app/state :<-> :username username
                            :token token}]]]))

(defn header
  "Create header for Basic authentication."
  [username password]
  (str "Basic " (b64/encodeString (str username ":" password))))

(defn auth!
  [user password handler failer]
  (ajax/GET (config/api "login")
            {:headers {"Authorization" (header user password)}
             :timeout 5000
             :handler handler
             :error-handler failer
             :format :transit}))