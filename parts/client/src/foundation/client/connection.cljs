(ns foundation.client.connection
  (:require [goog.events :refer [listen]]
            [goog.crypt.base64 :as b64]
            [cljs.core.async :as async :refer [alts!] :refer-macros [go alt!]]
            [cljs.core.async.interop :refer [p->c] :refer-macros [<p!]]
            [oops.core :refer [oget oset!]]
            [foundation.message :as fm :refer [->transit <-transit]]
            [foundation.client.config :as config]
            [foundation.client.logging :as log]
            [foundation.client.events]) ; think this is needed for defevent macro
  (:require-macros [foundation.client.api :refer [defevent]])
  (:import (goog.net WebSocket)
           (goog.net.WebSocket EventType))) ; != (goog.net EventType)

(defevent receive fm/receive)

; Websocket - validated on both sides

(defevent ws-open
  (fn [#_{:keys [user token]} open?]
    ;[[:send [:auth user token]]] ; TODO
    [[:db [{:app/state :<-> :online true}]]]
    [[:db [{:app/state :<-> :online false}]]]))

(defonce -websocket ; TODO reimplement directly on js/WebSocket?
  (let [ws (WebSocket.)]
    (listen ws EventType.OPENED
            (fn [_]
              (log/debug "Websocket open")
              (ws-open true)))
    (listen ws EventType.MESSAGE
            ; NB non-conforming messages would raise error because no corresponding multimethod?
            (fn [event] (-> event .-message fm/decode receive)))
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
  [msg] (.send -websocket (fm/encode msg)))

(defn websocket!
  "Connect or disconnect websocket."
  [action]
  (try (case action
         :connect (.open -websocket (config/api "ws" "ws"))
         :disconnect (.close -websocket))
       (catch :default e (log/error e))))

; Ajax - validated on both sides ; TODO endpoint and params validation?

(defn usp
  "Clojure map -> URLSearchParams"
  [params]
  (reduce (fn [usp [k v]] (.append usp (name k) v))
    (js/URLSearchParams.)
    params))

(defn fetch
  "More ergonomic js/fetch"
  [url {:keys [params] :as opts}]
  (let [url (oset! (js/URL. url) "search" (usp params))]
    (p->c (js/fetch url (-> (dissoc opts :params)
                            (update :headers #(js/Headers. (clj->js %)))
                            clj->js)))))

(defn http! [method url opts]
  (go (let [res-chan (fetch url (-> (assoc opts :method (name method) :mode "cors")
                                    (update :headers assoc :accept fm/transit-mime-type)))
            res (alt! res-chan ([v] v)
                  ; TODO AbortController https://davidwalsh.name/cancel-fetch
                  (async/timeout (:timeout config/config)) (ex-info "Timeout" {:error :timeout}))]
        (-> (if (instance? cljs.core/ExceptionInfo res)
              [::fm/error :fetch "Fetch failed" res]
              (case (oget res "status")
                200 (try (-> res .text p->c <! <-transit)
                         (catch js/Error e
                           [::fm/error :message "Unable to read fetched message" e]))
                401 [::fm/error :auth "Unauthenticated"]
                403 [::fm/error :auth "Unauthorised"]
                [::fm/error :fetch "Unsupported status" res]))
            fm/conform fm/receive))))

(defn get!
  ([endpoint] (get! endpoint {}))
  ([endpoint params]
   (http! :get (config/api endpoint) {:params params})))

(defn post!
  [endpoint msg]
  (http! :post (config/api endpoint) {:body (fm/encode msg)
                                      :headers {:content-type fm/transit-mime-type}}))

;[[:db [{:app/state :<-> :auth :fail}]]]
;[[:db [{:app/state :<-> :auth :error}]]])))

(defmethod fm/receive :auth [{:keys [username token]}]
  [[:db [{:app/state :<-> :username username :token token}]]])

(defn auth-header
  "Create header for Basic authentication (Authorization header)."
  ; TODO www-authenticate "Basic realm=\"default\"" from server
  [username password]
  (str "Basic " (b64/encodeString (str username ":" password))))