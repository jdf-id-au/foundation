(ns foundation.client.config
  (:require [clojure.edn :as edn]
            [clojure.java.shell :refer [sh]]
            [clojure.spec.alpha :as s]
            [foundation.spec :as fs]
            [taoensso.timbre :as log])
  (:refer-clojure :exclude [load])
  (:import (java.net NetworkInterface InetAddress)))

(defn version []
  (-> (sh "git" "describe" "--always") :out clojure.string/trim))

(defn host
  "Current site-local host address, for development."
  []
  (first (for [ifc (enumeration-seq (NetworkInterface/getNetworkInterfaces))
               addr (enumeration-seq (.getInetAddresses ifc))
               :when (.isSiteLocalAddress addr)]
           (.getHostAddress addr))))

(defn load
  ([] (load (-> "build-client.edn" slurp edn/read-string)))
  ([m] (let [config (cond-> (assoc m :version (version))
                      (some-> m :dev :host) (update-in [:dev :host]
                                                       #(case % :site-local (host), %)))]
         (println "Loading config" config)
         (if-let [explanation (s/explain-data ::fs/client-config config)]
           (do (log/error "Invalid config" explanation)
               (throw (ex-info "Invalid config" explanation)))
           config))))

(defmacro from-disk
  "Sneak config into client at compile time.
   Refreshing config can be difficult... need to modify this ns to trigger reload?"
  [] `~(load))