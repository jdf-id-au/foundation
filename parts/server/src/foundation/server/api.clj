(ns foundation.server.api
  "Common basic functionality for web applications."
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [foundation.spec :as fs]
            [foundation.config :as fc]
            [foundation.common.logging :as fl]
            [foundation.message :as fm]
            [clojure.java.io :as io]
            [talk.api :as talk]
            [clojure.core.async :as async :refer [chan go go-loop >! <! >!! <!!]]))

(def conform (partial fm/conform ::fm/->server))
(def validate (partial fm/validate ::fm/->client))

; Administration

(def cli-options
  "Basic set of options"
  [["-h" "--help" "Show this help text."]
   ["-c" "--config FILE" "Use specified config file."
    :default fc/config-filename
    :validate [#(s/valid? ::fs/config-file %) "No such config file."]]
   ["-p" "--port PORT" "Use specified port."
    :default 8000
    :parse-fn #(Integer/parseInt %)
    :validate [#(s/valid? ::fs/port %) "Please use port in range 8000-8999."]]
   ["-n" "--dry-run" "Run without doing anything important."]
   ["-l" "--log-level LEVEL" "Set log level."
    :default :info
    :parse-fn keyword
    :validate [#(s/valid? ::fs/log-level %) "Please use debug, info or warn."]]])

(def --repl
  "App needs to pull in nrepl dep."
  ["-r" "--repl PORT" "Provide nREPL on specified port."
   :parse-fn #(Integer/parseInt %)
   :validate [#(s/valid? ::fs/repl %) "Please use port in range 9000-9999."]])

;(def --allow-origin
;  ["-o" "--allow-origin HOST" "Allow api use from sites served at this (single) host."
;   :validate [#(s/valid? ::fs/allowed-origin %) "Invalid host."]])

(defn roll-up
  "Roll up relevant cli options into config (default: port and dry-run)."
  [spec {:keys [config] :as options} & additional-keys]
  (log/debug "Rolling up" spec "with" options "and" additional-keys)
  (merge (fc/load spec config) (select-keys options (conj additional-keys :port :dry-run))))

(defn validate-args
  [desc cli-options args]
  (let [{:keys [options arguments summary errors]} (cli/parse-opts args cli-options)
        {:keys [help log-level]} options
        summary (str desc \newline summary)]
    (cond errors {:exit [1 (apply str (interpose \newline (cons summary errors)))]}
          help {:exit [0 summary]}
          :else (do (fl/configure log-level)
                    {:options options :arguments arguments}))))

(defn exit
  [exit-code exit-message]
  (if (zero? exit-code) (log/info exit-message) ; ugh macros
                        (log/error exit-message))
  (System/exit exit-code))

(defn cli
  "Call from `-main`. Either exits or returns map of options and arguments."
  [desc cli-options args]
  (let [{exit-args :exit :as validated} (validate-args desc cli-options args)]
    (if exit-args (apply exit exit-args) validated)))

; Server

#_(defn server!
    "Set up http/websocket server using talk.api/server!
   Format its in/out chans with transit.
   Hook into auth system.
   Application needs to process unauth channel and call `auth`."
    [& args]
    (let [{:keys [clients] :as server} (apply talk/server! args)
          out (chan)
          _ (go-loop []
              (if-let [[username msg] (<! out)]))])) ; TODO validate
              ; TODO send Text or Binary to all user's ws connections, but response only to Request channel!