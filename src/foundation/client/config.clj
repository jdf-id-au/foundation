(ns foundation.client.config
  (:require [clojure.edn :as edn]
            [clojure.java.shell :refer [sh]])
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
                      (some-> m :dev :host) (update-in [:dev :host] #(case % :site-local (host), %)))]
         ; TODO validate config
         config)))

(defmacro config
  "Sneak config into client at compile time."
  [] `~(load))