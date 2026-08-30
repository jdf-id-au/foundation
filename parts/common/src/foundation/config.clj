(ns foundation.config
  "Aim to have project-local `config.edn` for reading at server launch, and
  `build-client.edn` for reading at client *build* time.

  During development, client is served by shadow-cljs on a port specified in `shadow-cljs.edn :dev-http`.
  This is currently matched manually by 
  "
  (:require [clojure.edn :as edn]
            [clojure.java.shell :refer [sh]]
            [clojure.spec.alpha :as s]
            [foundation.spec :as fs]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [comfort.core :as cc])
  (:refer-clojure :exclude [load])
  (:import (java.net NetworkInterface InetAddress)))

(defn version [] ; FIXME obviously doesn't work in production server
  (-> (sh "git" "describe" "--always") :out clojure.string/trim))

(defn host
  "Current site-local host address, for development."
  []
  #_(.getHostAddress (InetAddress/getLocalHost)) ; sometimes wrong/out of date?
  (first (for [ifc (enumeration-seq (NetworkInterface/getNetworkInterfaces))
               addr (enumeration-seq (.getInetAddresses ifc))
               :when (.isSiteLocalAddress addr)]
           (.getHostAddress addr))))

(defn lookup-host [h]
  (case h
    :localhost "127.0.0.1"
    :site-local (host)
    h))

(defn adjust
  "Inject version and interpret host"
  [m]
  (cond-> (assoc m :version (version))
    (some-> m :host) (update :host lookup-host)
    (some-> m :dev :host) (update-in [:dev :host] lookup-host)))

(def config-filename "config.edn")
(def client-build-config-filename "build-client.edn")

(defn validate
  [spec config]
  (if-let [explanation (s/explain-data spec config)]
    ;; TODO 2026-08-30 16:02:07 (cc/redact-keys ? :password) here too
    (do (log/error "Invalid config" {:explanation explanation})
        ;; https://ask.clojure.org/index.php/8313/ex-str-can-be-misleading-when-handling-s-explain-data
        (throw (ex-info "Invalid config" explanation)))
      config))

(defn load
  "Load config file and validate against spec."
  ([spec] (load spec config-filename))
  ([spec filename] (load spec filename identity))
  ([spec filename process]
   (let [f (io/file filename)]
     (if (.exists f)
       (let [config (->> filename slurp edn/read-string process)]
         (log/debug "Intepreting config" filename " " (cc/redact-keys config :password) "against" spec)
         (validate spec config))
       (println "No config" filename "found.")))))

(defmacro from-disk
  "Sneak config into client at compile time.
   Refreshing config can be difficult... need to modify this ns to trigger reload!"
  [] `~(load ::fs/client-config client-build-config-filename adjust))
