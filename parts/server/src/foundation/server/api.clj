(ns foundation.server.api
  "Common basic functionality for web applications."
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [foundation.spec :as fs]
            [foundation.config :as fc]
            [foundation.logging :as fl]
            [foundation.message :as fm :refer [->transit <-transit]]
            [foundation.server.http :as fsh]
            [clojure.java.io :as io]
            [talk.api :as talk]
            [clojure.core.async :as async :refer [chan go go-loop >! <! >!! <!!]]
            [bidi.bidi :as bidi])
  (:import (talk.http Connection Request Attribute File Trail)
           (talk.ws Text Binary))
  (:refer-clojure :exclude [send]))

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

(def conform (partial fm/conform ::fm/->server))
(def validate (partial fm/validate ::fm/->client))

(defprotocol Receive
  (receive [this server]))
(extend-protocol Receive
  Connection
  (receive [{:keys [channel state]} {:keys [clients] :as server}]
    (let [{:keys [username addr] :as client-meta} (get @clients channel)]
      ; Should be possible because clients map updated before connection and after disconnection?
      ; Unless async means meta's already gone. FIXME
      (log/info (or username "unknown")
        (case state :http "connected to" :ws "upgraded to ws on" nil "disconnected from")
        channel (when state (str "from " addr)))))
  Request
  (receive [{:keys [channel method path handler route-params parts] :as this}
            {:keys [routes clients] :as server}]
    #_(assert (not (or handler route-params parts)))
    (assert (nil? (get-in clients [channel :upload])) "Unprocessed upload at time of new request")
    ;(log/debug "Received" this)
    ;(log/debug "Matching" (bidi/match-route routes path))
    (let [req+route (merge this (bidi/match-route routes path))]
      (case method
        (:get :delete) (fsh/handler req+route server)
        (:post :put :patch) (swap! clients assoc-in [channel :upload] (assoc req+route :parts []))
        (:head :options :trace) (log/warn "Ignored HTTP" (name method) "request:" this)
        (log/error "Unsupported HTTP method" (name method) "in:" this))))
  Attribute
  (receive [{:keys [channel] :as this} {:keys [clients]}]
    ; TODO decode with ref to headers? e.g. application/transit+json
    (swap! clients update-in [channel :upload :parts] conj this))
  File
  (receive [{:keys [channel] :as this} {:keys [clients]}]
    (swap! clients update-in [channel :upload :parts] conj this))
  Trail ; NB relying on this ALWAYS appearing with PUT/POST/PATCH
  (receive [{:keys [channel cleanup] :as this} {:keys [clients] :as server}]
    ; NB problem is it seems to come across with plain GET too...
    (when-let [upload (get-in @clients [channel :upload])]
      (fsh/handler (update upload :parts conj this) server)
      (swap! clients update channel dissoc :upload))
    (cleanup))
  Text
  (receive [{:keys [channel text] :as this} server]
    (when-let [conformed (-> text <-transit conform)]
      (fm/receive (assoc conformed ::channel channel) server)))
  Binary
  (receive [{:keys [channel data] :as this} server]
    (if-let [conformed (conform [:binary data])]
      (fm/receive (assoc conformed ::channel channel) server))))

(defprotocol Send
  (send [this server]))
(extend-protocol Send
  Text
  (send [{:keys [channel text] :as this} {:keys [out] :as server}]
    (if-let [val+enc (some-> text validate ->transit)]
      (async/put! out (assoc this :text val+enc))))
  Binary
  (send [this {:keys [out] :as server}]
    (async/put! out this)))

(defn server!
  "Set up http+websocket server using talk.api/server!
   Deactivate ws by passing {:ws-path nil} in opts.
   Format in/out text ws chans with transit and dispatch messages via message/receive.
   TODO Format req/res guided by headers and dispatch reqs via message/handler (from http path via bidi).
   TODO Provide some auth mechanism for application to use!"
  [routes port & opts]
  (let [{:keys [clients] :as server} (-> (apply talk/server! port opts) (assoc :routes routes))
        _ (go-loop [msg (<! (server :in))]
            (if msg
              (do (try (receive msg server) ; NB currently sequential and blocking go; think about async/thread but unclear what if anything limits size of its thread pool
                       (catch Exception e
                         (log/error "Error handling incoming message" msg e)))
                  (recur (<! (server :in))))
              (log/warn "Tried to read from closed server in chan")))
        out (chan) ; only deals with websocket because http response is async/put! from handler
        _ (go-loop [msg (<! out)]
            (if msg
              (do (try (send msg server)
                       (catch Exception e
                         (log/error "Error handling outgoing message" msg e)))
                  (recur (<! out)))
              (log/warn "Tried to read from closed application out chan")))]
    (-> server (dissoc :in) (assoc :out out))))
  ; TODO [in application] send Text or Binary to all user's ws connections, but response only to Request channel!