(ns foundation.client.connection
  (:require [goog.crypt.base64 :as b64]
            [foundation.message :as fm]
            [foundation.client.config :as config]
            [foundation.client.logging :as log]
            [oops.core :refer [oget oset!]]))

;; ───────────────────────────────────────── Websocket - validated on both sides

(defn ws-open [#_{:keys [user token]} open?]
  ;;[[:send [:auth user token]]] ; TODO
  (if open?
    [[:db [{:app/state :<-> :online true}]]]
    [[:db [{:app/state :<-> :online false}]]]))


(defonce websocket (atom nil))

(defn send!
  "Send over websocket. Suitable for registration with nxr/register-effect!"
  [{:keys [dispatch]} system msg]
  (when @websocket
    (try (.send @websocket (fm/encode msg))
         (catch js/InvalidStateError e (log/error "Error sending." e)))))

(defn websocket!
  "Connect or disconnect websocket. Suitable for registration with nxr/register-effect!"
  [{:keys [dispatch]} system action]
  (try (case action
         :connect (reset! websocket
                    (doto (js/WebSocket. (config/api "ws" "ws"))
                      (.addEventListener "open" (fn [_] (log/debug "Websocket open") (dispatch (ws-open true))))
                      (.addEventListener "message" (fn [e] (-> e .-message fm/decode fm/receive)))
                      (.addEventListener "error" (fn [e] (log/error "Websocket error" (or (.-data e) ""))))
                      (.addEventListener "close" (fn [_] (log/debug "Websocket closed") (dispatch (ws-open false))))))
         :disconnect (do (some-> @websocket .close) (reset! websocket nil)))
       (catch :default e (log/error e))))

;; ──────────────────────────────────────────────────────────────────────── Ajax

(defn post!
  "Suitable for registration with nxr/register-effect!"
  [{:keys [dispatch]} system [endpoint msg] {:keys [on-success on-failure]}]
    (-> (js/fetch (if (vector? endpoint) (apply config/api endpoint) (config/api endpoint))
          #js {:method "POST" :body (fm/encode msg) :keepalive true
               :headers #js {:content-type fm/transit-mime-type :accept fm/transit-mime-type}})
      ;; TODO 2026-06-29 20:48:26 verify cookies come along for the ride
      (.then
        (fn [response]
          (case (oget response "status")
            200 (-> (.text response)
                  (.then
                    (fn [v] (-> v fm/decode fm/receive))
                    (fn [e] (log/warn "Failed to read" response))))
            401 (log/warn "Unauthenticated" response)
            403 (log/warn "Unauthorised" response)
            (log/warn "Unsupported status" response))
          (when on-success (dispatch on-success {:response response}))) ; "dispatch data will be merged into the original dispatch data"
        (fn [error] (if on-failure (dispatch on-failure {:error error})
                        (log/warn "Unhandled fetch error" error))))))

;; ──────────────────────────────────────────────────────────────────────── Auth

;; NB Client should retract any password from its db...
(defmethod fm/receive ::fm/auth [{:keys [username token]}]
  [[:db [{:app/state :<-> :username username :token token}]]])

(defn auth-header
  "Create header for Basic authentication (Authorization header)."
  ; TODO www-authenticate "Basic realm=\"default\"" from server
  [username password]
  (str "Basic " (b64/encodeString (str username ":" password))))
