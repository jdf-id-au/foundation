(ns foundation.server.http
  (:require [clojure.core.async :as async :refer [>!!]]
            [foundation.message :as fm]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [comfort.io :as cio])
  (:import (java.nio.file Path)
           (java.io File)))
  ;[yada.yada :as yada]
  ;[yada.handler]
  ;[aleph.http :as http]
  ;[manifold.deferred :as d]
  ;[byte-streams :as bs]
  ;[manifold.stream :as st]
  ;[buddy.core.nonce :as nonce]
  ;  (:import (org.bouncycastle.util.encoders Hex)))

; This can wait -- prioritise viz/local!

; Recaptcha

;(defn recaptcha!
;  "Verify recaptcha synchronously."
;  [secret response]
;  @(-> (http/post "https://www.google.com/recaptcha/api/siteverify"
;                  {:form-params {:secret secret :response response}})
;       (d/chain :body bs/to-string #(json/read-str % :key-fn keyword))
;       (d/catch (fn [e] (log/warn "Error verifying reCAPTCHA"
;                                  (.getClass e) (.getMessage e))))))

;(defn human?
;  [{:keys [parameters] :as ctx}
;   {:keys [recaptcha-secret] :as config}]
;  (let [{:keys [g-recaptcha-response]} (:form parameters)
;        {:keys [success score]} (recaptcha! recaptcha-secret g-recaptcha-response)]
;    (and success (< 0.5 score))))

; Ajax

;(defn ajax-send
;  "Send `msg` reply. See `f.common.message` specs."
;  [msg]
;  (let [[type & _ :as validated]
;        (or (validate msg) [:error :outgoing "Problem generating server reply." msg])]
;    (if (= type :error) (log/warn "Telling user about server error" msg))
;    (->transit validated)))
;
;(defn ajax-receive
;  "Acknowledge request with appropriate reply."
;  [msg]
;  (ajax-send (if-let [conformed (conform msg)]
;               (message/receive nil nil conformed)
;               [:error :incoming "Invalid message sent to server." msg])))

; Server

;(defn add-nonce [ctx]
;  (assoc ctx :nonce (-> 8 nonce/random-bytes Hex/toHexString)))

;(defn add-csp
;  "Work around inability to pass function in :content-security-policy."
;  [{:keys [nonce] :as ctx}]
;  (assoc-in ctx [:response :headers "content-security-policy"]
;            ; this is such a mess, possibly "worse" on firefox
;            ; https://github.com/google/recaptcha/issues/107
;            (str "script-src 'unsafe-eval' 'nonce-" nonce "';")))

;#_(def example-routes
;    ["/" {"" (server/resource
;               {:methods {; TODO deal with GET queries :parameters {:query :spec?}...
;                          :get {:produces "text/html"
;                                :response :response?}
;                          :post {:consumes "application/x-www-form-urlencoded"
;                                 :parameters {:form :schema?}
;                                 :produces "text/html"
;                                 :response :response?}}
;                :responses {400 {:produces "text/html"
;                                 :response :response?}}})}])
;
;(defn resource
;  "Make yada resource with nonce and Content Security Policy."
;  [resource-map]
;  (-> (yada/resource (assoc resource-map
;                       ; not sure why this needs to be added explicitly
;                       :interceptor-chain yada/default-interceptor-chain))
;      (yada.handler/prepend-interceptor add-nonce)
;      (yada.handler/append-interceptor yada.security/security-headers add-csp)))

(defmulti handler
  (fn dispatch [{:keys [handler] :as request} {:keys [out] :as server}] handler))
(defmethod handler :default [{:keys [channel] :as request} {:keys [out] :as server}]
  (log/debug "No handler for request" request)
  (async/put! out {:channel channel :status 404}))

(def content-types
  {:html "text/html; charset=utf-8"
   :css "text/css; charset=utf-8"
   :js "text/javascript; charset=utf-8"
   :csv "text/csv; charset=utf-8"
   :json "application/json; charset=utf-8"
   :jpg "image/jpeg"
   :png "image/png"
   :gif "image/gif"})

(defmethod handler ::file [{:keys [channel method path headers] :as request}
                           {:keys [out] :as server}]
  ; TODO 304 semantics
  (case method
    :get
    (if-let [^File safe-local (cio/safe-subpath "public"
                                (case path "/" "index.html" path))] ; deliberately hardcoded "public/"
      (if-let [content-type (some-> safe-local cio/get-extension keyword content-types)]
        (async/put! out {:channel channel :status 200
                         ; TOOD parse :accept header (without bringing in a million deps)
                         ; https://tools.ietf.org/html/rfc7231#section-5.3.2
                         :headers {:content-type content-type
                                   :x-content-type-options "nosniff"}
                         :content safe-local})
        (async/put! out {:channel channel :status 404}))
      (async/put! out {:channel channel :status 404}))
    (async/put! out {:channel channel :status 405})))

(defmethod handler ::message [{:keys [channel method headers body cleanup] :as request}
                              {:keys [out] :as server}]
  ; Mark endpoint as ajax interface to foundation.message system.
  ; f.message/receive for these messages should return a valid message
  ; which this handler sends back to the client in a talk.http/response
  (if-let [{:keys [type code] :as msg}
           (fm/conform (cond (not= :post method) [::fm/error :method "POST only"]
                             (not= fm/transit-mime-type (:content-type body))
                             [::fm/error :content-type "Wrong content type"]
                             :else (fm/<-transit (:value body))))]
    (async/put! out
      {:channel channel
       :status (case type
                 ::fm/error (case code :message 400 :method 405 :content-type 415
                              500)
                 200)
       :headers {:content-type fm/transit-mime-type}
       :content (fm/receive msg)}
      (fn [_] (cleanup)))
    (do (log/error "Problem handling ::message" request)
        (async/put! out {:channel channel :status 500})
        (cleanup))))

#_(defmethod handler ::ajax [{:keys [channel meta method headers path
                                     parameters body parts cleanup
                                     handler route-params] :as request}
                             {:keys [out] :as server}]
    (let [msg (case method :get ; reuse handler kw as message kw
                           [handler route-params parameters]
                           :post
                           (or body [handler route-params parameters parts]))]))