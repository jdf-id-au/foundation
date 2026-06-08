(ns user
  (:require [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.api :as shadow]
            [foundation.server.api :as api]
            [foundation.server.http :as http]
            [foundation.logging :as fl]
            [foundation.spec :as fs]
            [common]
            [foundation.message :as message]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]))

(defn client! "Start shadow-cljs server with reload." []
  (server/start!)
  (shadow/watch :app))

(defn restart-client! []
  (server/stop!)
  (client!))

(defmethod http/handler :hello [{:keys [channel method path headers] :as request}
                                {:keys [out] :as server
                                 {:keys [::fs/allow-origin]} :opts}]
  #_(log/debug "hello handler" request)
  (case method
    :get (http/respond! request server {:status 200 :headers {:content-type "text/plain"}
                                        :content "hello"})
    :options ; manual preflight
    (if (= (:access-control-request-method headers) "POST")
      ;; TODO 2026-06-08 22:46:26 maybe :access-control-request-headers
      ;; TODO 2026-06-08 22:48:57 :access-control-allow-credentials
      ;; https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS
      ;; TODO 2026-06-08 23:04:09 cors middleware which depends on handler;
      ;; slightly harder because respond!ing directly from handler
      (http/respond! request server {:status 204
                                     :headers {:access-control-allow-origin allow-origin
                                               :access-control-allow-methods "POST, GET, OPTIONS"
                                               ;; Chrome requires this
                                               :access-control-allow-headers "Content-Type"}}))
    :post
    (http/respond! request server {:status 200
                                   :headers {:content-type "application/transit+json"
                                             ;; Chrome requires this
                                             :access-control-allow-origin allow-origin}
                                   :content (message/encode [:pong :yay "really"])})
    (http/respond! request server {:status 405})))

(defn cljs "Start cljs repl." [] (shadow/repl :app))

(fl/configure :debug)

(comment
  (def s (api/server! 8126
           ["" [["/" {"hello" :hello
                      "ws" ::api/ws}]
                [true ::http/file]]]
           {::fs/allow-origin "http://localhost:8888"}))
  ((:close s))
  (client!) ; then visit http://localhost:8888/
  (restart-client!)
  (cljs)
  :cljs/quit)
