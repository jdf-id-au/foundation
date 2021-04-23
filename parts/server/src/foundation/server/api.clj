(ns foundation.server.api
  "Common basic functionality for web applications."
  (:require [clojure.spec.alpha :as s]
            [clojure.tools.cli :as cli]
            [taoensso.timbre :as log]
            [foundation.spec :as fs]
            [foundation.config :as fc]
            [foundation.logging :as fl]
            [foundation.message :as fm :refer [->transit <-transit]]
            [foundation.server.http :as fsh]
            [talk.api :as talk]
            [talk.ws :refer [->Text]]
            [talk.util :refer [ess]]
            [clojure.core.async :as async :refer [chan go go-loop >! <! >!! <!!]]
            [bidi.bidi :as bidi])
  (:import (talk.http Connection Request Attribute File Trail)
           (talk.ws Text Binary)
           (clojure.lang APersistentMap)
           (java.nio.charset Charset)
           (java.nio ByteBuffer)
           (java.io FileReader FileInputStream)))

; Administration

(def cli-options
  "Basic set of options.
   Don't provide defaults: they will inappropriately override config file (see `roll-up`)."
  [["-h" "--help" "Show this help text."]
   ["-c" "--config FILE" "Use specified config file."
    :default fc/config-filename
    :validate [#(s/valid? ::fs/config-file %) "No such config file."]]
   ["-p" "--port PORT" "Use specified port."
    :parse-fn #(Integer/parseInt %)
    :validate [#(s/valid? ::fs/port %) "Please use port in range 8000-8999."]]
   ["-n" "--dry-run" "Run without doing anything important."]
   ["-l" "--log-level LEVEL" "Set log level."
    :parse-fn keyword
    :validate [#(s/valid? ::fs/log-level %) "Please use debug, info or warn."]]])

(def --repl
  "App needs to pull in nrepl dep."
  ["-r" "--repl PORT" "Provide nREPL on specified port."
   :parse-fn #(Integer/parseInt %)
   :validate [#(s/valid? ::fs/repl %) "Please use port in range 9000-9999."]])

(def --allow-origin
  ["-o" "--allow-origin HOST" "Allow api use from sites served at this (single) host."
   :validate [#(s/valid? ::fs/allowed-origin %) "Invalid host."]])

(defn roll-up
  "Roll up relevant cli options into config (always includes: port, dry-run, log-level).
   This lets config be overridden by cli options."
  [spec {:keys [config] :as options} & additional-keys]
  (let [key-list (conj additional-keys :port :dry-run :log-level)]
    (log/debug "Rolling up" spec "with" options "and" key-list)
    (merge (fc/load spec config) (select-keys options key-list))))

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

(defprotocol Receivable
  (receive [this server] "Receive typed message from talk server.")
  (present [this] "Convert to practical representation."))
(extend-protocol Receivable
  Connection
  (receive [{:keys [channel state]} {:keys [clients] :as server}]
    (let [{:keys [username addr]} (get clients channel)]
      (log/info (or username "unknown")
        (case state :http "connected to" :ws "upgraded to ws on" nil "disconnected from")
        (ess channel) (when state (str "from " addr)))))
  (present [_])
  Request
  (receive [{:keys [channel method path handler route-params parts] :as this}
            {:keys [routes clients out] :as server}]
    #_(assert (not (or handler route-params parts)))
    (assert (nil? (get-in clients [channel :assemble])) "Received new request while already assembling one.")
    ;(log/debug "Received" this)
    ;(log/debug "Matching" (bidi/match-route routes path))
    (let [req+handler (merge this (bidi/match-route routes path))] ; adds :handler and :route-params
      (case method
        (:get :delete) (fsh/handler req+handler server)
        (:post :put :patch)
        (do (assoc-in clients [channel :assemble] (assoc req+handler :parts []))
            (async/put! out {:channel channel :status 102})) ; permit upload
        (:head :options :trace) (log/warn "Ignored HTTP" (name method) "request:" this)
        (log/error "Unsupported HTTP method" (name method) "in:" this))))
  (present [this] this)
  Attribute
  (receive [{:keys [channel] :as this} {:keys [clients]}]
    (update-in clients [channel :assemble :parts] conj this))
  (present [{:keys [name ^Charset charset file? value] :as this}]
    {name (if file?
            (FileReader. ^java.io.File value charset)
            (->> value ByteBuffer/wrap (.decode charset)))})
  File
  (receive [{:keys [channel] :as this} {:keys [clients]}]
    (update-in clients [channel :assemble :parts] conj this))
  (present [{:keys [name filename ^Charset charset content-type file? value]}]
    (let [binary? (= content-type "application/octet-stream")]
      {[name filename] (if file?
                         (if binary?
                           (FileInputStream. ^java.io.File value)
                           (FileReader. ^java.io.File value charset))
                         (if binary?
                           value
                           (->> value ByteBuffer/wrap (.decode charset))))}))
  Trail
  ; NB relying on this ALWAYS appearing with PUT/POST/PATCH
  ; NB application needs to run (cleanup) when finished
  (receive [{:keys [channel cleanup] :as this} {:keys [clients] :as server}]
    (if-let [{:keys [parts] :as req+handler+parts} (get-in clients [channel :assemble])]
      (try (let [simple? (and (= 1 (count parts))
                           (= "payload" (-> parts first :name))) ; from `fake-decoder`
                 assembled
                 (cond-> (assoc req+handler+parts :cleanup cleanup)
                   simple? (-> (dissoc :parts) (assoc :body (first parts)))
                   ; group-by will cause :parts vals to be vectors, even if only one part
                   (not simple?) (assoc :parts (group-by (comp keyword name) parts)))]
             (fsh/handler assembled server)
             (update clients channel dissoc :assemble))
           (catch Exception e
             (cleanup)
             (log/error "Error assembling request" req+handler+parts e)))
      (cleanup)))
  (present [{:keys [headers]}] headers)
  Text
  (receive [{:keys [channel text] :as this} server]
    (when-let [conformed (fm/decode text)]
      (fm/receive (assoc conformed ::channel channel) server)))
  (present [{:keys [text]}] (fm/decode text))
  Binary
  (receive [{:keys [channel data] :as this} server]
    (if-let [conformed (fm/conform [::fm/binary data])]
      (fm/receive (assoc conformed ::channel channel) server)))
  (present [{:keys [data]}] data))

(defprotocol Sendable
  (send! [this server] "Send typed message to talk server."))
(extend-protocol Sendable
  Text
  (send! [this {:keys [out]}]
    (when-not (async/put! out this)
      (log/warn "Dropped outgoing message because application out chan is closed" (str this))))
  Binary
  (send! [this {:keys [out]}]
    (when-not (async/put! out this)
      (log/warn "Dropped outgoing message because application out chan is closed" (str this))))
  APersistentMap ; {"username" [:message :to :validate]}
  (send! [this {:keys [clients] :as server}]
    ; This is for sending ws messages to users, no matter how many connections they have.
    ; Binary not supported yet -- send directly to channel.
    (doseq
      [[to-user msg] this
       :let [[type & _ :as validated]
             (or (fm/validate msg) [::fm/error :outgoing "Problem generating server reply." msg])
             transit (->transit validated)
             _ (when (= type :error) (log/warn "Telling" to-user "about server error" msg))]
       channel (reduce (fn [agg [ch {:keys [type username]}]]
                         (if (and (= type :ws) (= username to-user))
                           (conj agg ch)
                           agg))
                 #{}
                 clients)]
      (send! (->Text channel transit) server))))

(defn server!
  "Set up http+websocket server using talk.api/server!
   Format in/out text ws chans with transit and dispatch messages via message/receive.
   TODO Format req/res guided by headers and dispatch reqs via message/handler (from http path via bidi).
   TODO Provide some auth mechanism for application to use!"
  ([port routes] (server! port routes nil))
  ([port routes opts]
   (let [ws-path (bidi/path-for routes ::ws)
         opts (cond-> opts ws-path (assoc :ws-path ws-path))
         server (-> (talk/server! port opts)
                    (assoc :routes routes :opts opts))
         _ (go-loop [msg (<! (server :in))]
             (if msg
               (do (try (receive msg server) ; NB currently sequential and blocking go; think about async/thread but unclear what if anything limits size of its thread pool
                        (catch Exception e
                          (log/error "Error handling incoming message" msg e)))
                   (recur (<! (server :in))))
               (log/warn "Tried to read from closed server in chan")))
         ; NB this chan is only for websocket because http response is async/put! from handler
         out (chan)
         _ (go-loop [msg (<! out)]
             (if msg
               (do (try (send! msg server) ; TODO catch closed out chan etc
                        (catch Exception e
                          (log/error "Error handling outgoing message" msg e)))
                   (recur (<! out)))
               (log/warn "Tried to read from closed application out chan")))]
     (-> server (dissoc :in) (assoc :out out)))))