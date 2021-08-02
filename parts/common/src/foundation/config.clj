(ns foundation.config
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

(defn configure-client
  "Inject version and interpret host"
  [m]
  (cond-> (assoc m :version (version))
    (some-> m :dev :host) (update-in [:dev :host] #(case % :site-local (host), %))))

(def config-filename "config.edn")

(defn load
  "Load config file and validate against spec."
  ([spec] (load spec config-filename))
  ([spec filename] (load spec filename identity))
  ([spec filename process]
   (let [f (io/file filename)]
     (if (.exists f)
       (let [config (->> filename slurp edn/read-string process)]
         (log/debug "Intepreting config" (cc/redact-keys config :password)  "against" spec)
         (if-let [explanation (s/explain-data spec config)]
           (do (log/error "Invalid config" {:explanation explanation})
               ; https://ask.clojure.org/index.php/8313/ex-str-can-be-misleading-when-handling-s-explain-data
               (throw (ex-info "Invalid config" explanation)))
           config))
       (println "No config" filename "found.")))))

(defmacro from-disk
  "Sneak config into client at compile time.
   Refreshing config can be difficult... need to modify this ns to trigger reload!"
  [] `~(load ::fs/client-config "build-client.edn" configure-client))