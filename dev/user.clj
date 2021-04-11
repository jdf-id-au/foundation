(ns user
  (:require [shadow.cljs.devtools.server :as server]
            [shadow.cljs.devtools.api :as shadow]
            [foundation.server.api :as api]
            [foundation.server.http :as http]
            [foundation.logging :as fl]))

(defn client! "Start shadow-cljs server with reload." []
  (server/start!)
  (shadow/watch :app))

(defn restart-client! []
  (server/stop!)
  (client!))

(defn cljs "Start cljs repl." [] (shadow/repl :app))

(fl/configure :debug)

#_ (client!)
#_(def s (api/server! ["" [["/" {"hello" :hello
                                 "ws" :ws}]
                           [true ::http/file]]] ; catchall
           8126))
#_ ((:close s))