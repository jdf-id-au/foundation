(ns foundation.client.connection
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

(def conform (partial message/conform ::message/->client))
(def validate (partial message/validate ::message/->server))

(defevent receive message/receive)

; Websocket - validated on both sides

(defevent ws-open
  (fn [#_{:keys [user token]} open?]
    ;[[:send [:auth user token]]] ; TODO
    [[:db [{:app/state :<-> :online true}]]]
    [[:db [{:app/state :<-> :online false}]]]))

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

; Ajax - validated on both sides ; TODO endpoint and params validation?

(defn ajax-handler [response] (-> response conform receive))
(defn ajax-failer [response] (log/error "Ajax error" response)) ; TODO tell user, see auth-failer
(def ajax-response "Add transit handlers for tick classes."
  (ajax/transit-response-format (assoc message/read-handlers :type :json)))
(def ajax-request "Add transit handlers for tick classes."
  (ajax/transit-request-format (assoc message/write-handlers :type :json)))

(defn get!
  "Ajax query"
  ([endpoint] (get! endpoint {}))
  ([endpoint params]
   (ajax/GET (config/api endpoint)
             {:timeout (:timeout config/config)
              :handler ajax-handler
              :error-handler ajax-failer
              :params params
              :response-format ajax-response})))

(defn post!
  "Ajax command"
  [endpoint message] ; ARRGH not formatting correctly
  (log/debug "Trying to send" message "to" endpoint)
  (ajax/POST (config/api endpoint)
             {:timeout (:timeout config/config)
              :handler ajax-handler
              :error-handler ajax-failer
              :headers {:content-type "application/transit+json"}
              :body (some-> message validate ->transit)
              :response-format ajax-response}))

; TODO could do delete!

; Auth

;(defevent auth-failer ; NB example only
;  (fn [{:keys [status] :as response}]
;    (log/info "Auth failure" response)
;    (case status
;      ; wrong credentials:
;      #_{:status 401 :status-text "Unauthorized." :failure :error
;         :response {:cause "No authorization provided"
;                    :data {:status 401}
;                    :headers {"www-authenticate" ["Basic realm=\"default\""]}}}
;      401 [[:db [{:app/state :<-> :auth :fail}]]]
;      ; server not running:
;      #_{:status 0 :status-text "Request failed." :failure :failed}
;      [[:db [{:app/state :<-> :auth :error}]]])))
;
;(defmethod message/receive :auth [{:keys [username token]}]
;  [[:db [{:app/state :<-> :username username :token token}]]])
;
;(defn header
;  "Create header for Basic authentication."
;  [username password]
;  (str "Basic " (b64/encodeString (str username ":" password))))
;
;(defn auth!
;  [user password handler failer]
;  (ajax/GET (config/api "login")
;            {:headers {"Authorization" (header user password)}
;             :timeout 5000
;             :handler ajax-handler
;             :error-handler failer
;             :format :transit}))