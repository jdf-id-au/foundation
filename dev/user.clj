(ns user
  (:require [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.api :as shadow]
            [foundation.server.api :as fs]
            [foundation.server.http :as http]
            [foundation.logging :as fl]
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
  (log/debug "hello handler" request)
  (case method
    :get {:status 200 :headers {:content-type "text/plain"}
          :content "hello"}
    :options (fs/allow allow-origin #{:post} #{:content-type})
    :post
    {:status 200
     :headers {:content-type "application/transit+json"
               ;; Chrome requires this
               :access-control-allow-origin allow-origin}
     :content (message/encode [:pong :yay "really"])}
    {:status 405}))

(defn cljs "Start cljs repl." [] (shadow/repl :app))

(fl/configure :debug)

(comment
  (def s (fs/server! 8126
           ["" [["/" {"hello" :hello
                      "ws" ::fs/ws}]
                [true ::http/file]]]
           nil ; ctx
           {::fs/allow-origin "http://localhost:8888"}))
  ((:close s))
  (client!) ; then visit http://localhost:8888/
  (restart-client!)
  (cljs)
  :cljs/quit
  )
