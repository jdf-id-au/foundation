(ns foundation.server.api
  "Common basic functionality for web applications."
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [foundation.spec :as fs]
            [foundation.common.logging :as fl]
            [foundation.message :as message]
            [clojure.java.io :as io]
            [sok.api :as sok]
            [clojure.core.async :as async :refer [chan go go-loop >! <! >!! <!!]]
            [foundation.config :as config]))



(def conform (partial message/conform ::message/->server))
(def validate (partial message/validate ::message/->client))

;(def clients "Map of websocket-> nothing yet!" (atom {})) ; TODO ***
;
;(defn ws-send
;  "Send `msg` to connected `user`s over their registered websocket/s.
;  See `f.common.message` specs."
;  [user-msg-map]
;  (doseq [[user msg] user-msg-map
;          :let [[type & _ :as validated]
;                (or (validate msg) [:error :outgoing "Problem generating server reply." msg])
;                [ws u] @clients]
;          :when (= u user)]
;    (if (= type :error) (log/warn "Telling" user "about server error" msg))
;    (-> (st/put! ws validated)
;        (d/chain #(if-not % (log/info "Failed to send" msg "to" user)
;                            #_ (log/debug "Sent" msg "to" user)))
;        (d/catch #(log/info "Error sending" msg "to" user %)))))





;(defn ws-receive
;  "Acknowledge request with appropriate reply."
;  [user msg]
;  (ws-send (if-let [conformed (conform msg)]
;             (message/receive clients user conformed)
;             {nil [:error :incoming "Invalid message sent to server." msg]})))

;(defn setup-websocket
;  "Maintain registry of clients" ; TODO *** and all the rest! see Leavetracker
;  ; TODO need to have some idea who's who; could do by ip?
;  ; better not to open ws for allcomers, should auth somehow first?
;  [ws]
;  (let [formatted-ws (format-stream ws ->transit <-transit)
;        ws-hash (hash formatted-ws)]
;    (log/debug "Preparing websocket" ws-hash)
;    (st/on-closed formatted-ws (fn [] (log/debug "Disconnected" ws-hash)
;                                      (swap! clients dissoc formatted-ws)))
;    (swap! clients assoc formatted-ws :nothing-useful-yet)
;    (st/put! formatted-ws [:ready])
;    (log/debug "Web socket ready" ws-hash)
;    (st/consume (partial ws-receive) formatted-ws)))

;(defn websocket-handler [req]
;  ; FIXME need to harden this public exposed endpoint
;  (-> (http/websocket-connection req)
;      (d/chain setup-websocket)
;      (d/catch (fn [& args]
;                 (log/info "Websocket exception" args)
;                 {:status 400
;                  :headers {"Content-type" "text/plain"}
;                  :body "Expected a websocket request"}))))



; Administration

(def config-filename "config.edn")

(def cli-options
  "Basic set of options"
  [["-h" "--help" "Show this help text."]
   ["-c" "--config FILE" "Use specified config file."
    :default config-filename
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
  (merge (config/load spec config) (select-keys options (conj additional-keys :port :dry-run))))

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