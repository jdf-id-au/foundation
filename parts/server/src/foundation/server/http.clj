(ns foundation.server.http
  (:require [clojure.core.async :as async :refer [>!!]]
            [foundation.message :as fm]
            [taoensso.timbre :as log]
            [comfort.io :as cio]
            [hato.client :as hc])
  (:import (java.io File)))
  ;[buddy.core.nonce :as nonce]
  ;  (:import (org.bouncycastle.util.encoders Hex)))

; This can wait -- prioritise viz/local!

; Recaptcha

#_(defonce http-client (hc/build-http-client {})) ; FIXME possibly move to being application's responsibility

#_(defn recaptcha!
    "Verify recaptcha synchronously. Pass in hato http-client."
    [secret response]
    (try
      (-> (hc/post "https://www.google.com/recaptcha/api/siteverify"
            {:http-client http-client :form-params {:secret secret :response response}})
          :body) ; TODO check keys are keywordified
      (catch Exception e
        (log/warn "Error verifying reCAPTCHA" (.getClass e) (.getMessage e)))))

#_(defn human?
    [recaptcha-secret g-recaptcha-response]
    (let [{:keys [success score]} (recaptcha! recaptcha-secret g-recaptcha-response)]
      (and success (< 0.5 score))))

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

(defn respond!
  "Add channel to request and approve any POST/PUT/PATCH."
  [{:keys [channel method] :as request} {:keys [out opts] :as server} rest-of-response & args]
  (let [[fn1 on-caller?] args]
    (log/debug "FSHTTP PUT!")
    ; FIXME *** roughly alternate js/fetch post requests are closing channel prematurely while server is trying to respond (chrome/ff/safari, each with different error messages, some relating to CORS, which probably come from the fact that the channel closes prematurely);
    ; maybe monitoring artifact re keep-alive https://github.com/google/tamperchrome/discussions/134
    ; maybe need one more (.read ctx) ??
    (when-not (apply async/put! out (log/spy :debug (-> rest-of-response
                                                     (assoc :channel channel)
                                                     #_(update :headers
                                                         assoc :access-control-allow-origin
                                                         (:allow-origin opts)))) args)
      (log/warn "Failed to send http response because out chan closed."))))

(defmethod handler ::file [{:keys [method path] :as request} server]
  ; TODO 304 semantics
  (case method
    :get
    (if-let [^File safe-local (cio/safe-subpath "public"
                                (case path "/" "index.html" path))] ; deliberately hardcoded "public/"
      (if-let [content-type (some-> safe-local cio/get-extension keyword content-types)]

        (respond! request server
          {:status 200
           ; TOOD parse :accept header (without bringing in a million deps)
           ; https://tools.ietf.org/html/rfc7231#section-5.3.2
           :headers {:content-type content-type
                     :x-content-type-options "nosniff"}
           :content safe-local})
        (respond! request server {:status 404}))
      (respond! request server {:status 404}))
    (respond! request server {:status 405})))

(defmethod handler ::message [{:keys [channel method headers body cleanup] :as request}
                              {:keys [out] :as server}]
  ; Mark endpoint as ajax interface to foundation.message system.
  ; f.message/receive for these messages should return a valid message
  ; which this handler sends back to the client in a talk.http/response
  (let [{:keys [type code] :as msg}
        (fm/conform (cond (not= :post method) [::fm/error :method "POST only"]
                          (not= fm/transit-mime-type (:content-type body))
                          [::fm/error :content-type "Wrong content type"]
                          :else (fm/<-transit (:value body))))
        [reply-type reply-code & _ :as reply]
        (case type ::fm/error nil
          (or (fm/validate (fm/receive msg))
              [::fm/error :outgoing "Problem generating server reply" msg]))]
    (respond! request server
      {:status (case type
                 ::fm/error (case code :message 400 :method 405 :content-type 415 500)
                 (case reply-type ::fm/error (case reply-code :outgoing 500 500) 200))
       :headers {:content-type fm/transit-mime-type}
       :content (fm/->transit reply)}
      (fn [_] (cleanup)))))

#_(defmethod handler ::ajax [{:keys [channel meta method headers path
                                     parameters body parts cleanup
                                     handler route-params] :as request}
                             {:keys [out] :as server}]
    (let [msg (case method :get ; reuse handler kw as message kw
                           [handler route-params parameters]
                           :post
                           (or body [handler route-params parameters parts]))]))