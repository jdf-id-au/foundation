(ns foundation.client.connection
  (:require [goog.crypt.base64 :as b64]
            [foundation.message :as fm]
            [foundation.client.config :as config]
            [foundation.client.logging :as log]))

;; ───────────────────────────────────────── Websocket - validated on both sides

(defn ws-open [#_{:keys [user token]} open?]
  ;;[[:send [:auth user token]]] ; TODO
  (if open?
    [[:db [{:app/state :<-> :online true}]]]
    [[:db [{:app/state :<-> :online false}]]]))


(defonce websocket (atom nil))

(defn send!
  "Send over websocket."
  [msg]
  #_(log/debug "Sending " msg)
  (when @websocket
    (try (.send @websocket (fm/encode msg))
         #_(catch AssertionError e
             (log/warn "Error sending. Is ws open?" e))
         (catch js/Error e
           (log/error "Error sending." e)))))

(defn websocket!
  "Connect or disconnect websocket."
  [action]
  (try (case action
         :connect (reset! websocket
                    (doto (js/WebSocket. (config/api "ws" "ws"))
                      (.addEventListener "open" (fn [_] (log/debug "Websocket open") (ws-open true)))
                      (.addEventListener "message" (fn [e] (-> e .-message fm/decode fm/receive)))
                      (.addEventListener "error" (fn [e] (log/error "Websocket error" (or (.-data e) ""))))
                      (.addEventListener "close" (fn [_] (log/debug "Websocket closed") (ws-open false)))))
         :disconnect (do (some-> @websocket .close) (reset! websocket nil)))
       (catch :default e (log/error e))))

;; ──────────────────────────────────────────────────────────────────────── Ajax



;; ──────────────────────────────────────────────────────────────────────── Auth

;; NB Client should retract any password from its db...
(defmethod fm/receive ::fm/auth [{:keys [username token]}]
  [[:db [{:app/state :<-> :username username :token token}]]])

(defn auth-header
  "Create header for Basic authentication (Authorization header)."
  ; TODO www-authenticate "Basic realm=\"default\"" from server
  [username password]
  (str "Basic " (b64/encodeString (str username ":" password))))
