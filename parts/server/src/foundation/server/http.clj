(ns foundation.server.http
  (:require [clojure.core.async :as async :refer [>!!]]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [comfort.io :as cio])
  (:import (java.nio.file Path)
           (java.net URLConnection)
           (java.io File)))
  ;[yada.yada :as yada]
  ;[yada.handler]
  ;[aleph.http :as http]
  ;[manifold.deferred :as d]
  ;[byte-streams :as bs]
  ;[manifold.stream :as st]
  ;[buddy.core.nonce :as nonce]
  ;  (:import (org.bouncycastle.util.encoders Hex)))

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
  (fn dispatch [{:keys [handler]} _] handler))
(defmethod handler :default [{:keys [channel] :as request} {:keys [out] :as server}]
  (log/debug "No handler for request" request)
  (async/put! out {:channel channel :status 404}))

(def content-types
  {:html "text/html"
   :css "text/css"
   :js "text/javascript"
   :jpg "image/jpeg"
   :png "image/png"
   :gif "image/gif"})

(defmethod handler ::file [{:keys [channel method path headers] :as request}
                           {:keys [out] :as server}]
  (log/debug "handling path" path)
  (case method
    :get
    (if-let [^File safe-local (cio/safe-subpath "public"
                                (case path "/" "index.html" path))] ; deliberately hardcoded "public/"
      ; URLConnection/guessContentTypeFromName doesn't have .js !
      (if-let [content-type (-> safe-local cio/get-extension keyword content-types)]
        (async/put! out {:channel channel :status 200
                         ; TOOD parse :accept header (without bringing in a million deps)
                         ; https://tools.ietf.org/html/rfc7231#section-5.3.2
                         :headers {:content-type content-type}
                         :content safe-local})
        (async/put! out {:channel channel :status 404}))
      (async/put! out {:channel channel :status 404}))
    (async/put! out {:channel channel :status 405})))