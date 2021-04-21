(ns user
  (:require [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.api :as shadow]
            [foundation.server.api :as api]
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
                                {:keys [out] :as server}]
  #_(log/debug "hello handler for" request)
  (case method
    :get (async/put! out {:channel channel :status 200 :headers {:content-type "text/plain"}
                          :content "hello"})
    :post
    (async/put! out {:channel channel :status 200
                     :headers {:content-type "application/transit+json"}
                     :content (message/encode [:pong :yay "really"])})
    (async/put! out {:channel channel :status 405})))

(defn cljs "Start cljs repl." [] (shadow/repl :app))

(fl/configure :debug)

#_(def s (api/server! 8126
           ["" [["/" {"hello" :hello
                      ;"login" ::http/login
                      ;"logout" ::http/logout
                      "ws" ::api/ws}]
                [true ::http/file]]]
           {:allow-origin "http://localhost:8888"})) ; catchall

; curl -v -X POST http://localhost:8126/hello -H "content-type:text/plain" -H "content-length: 0"
; but getting invalid version format with ajax post **because chrome using ssl!

#_ (client!) ; then visit http://localhost:8888/
#_ (restart-client!)
#_ (cljs)
#_ :cljs/quit

#_ ((:close s))