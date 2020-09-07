(ns foundation.server
  "Common basic functionality for web applications."
  (:require [yada.yada :as yada]
            [yada.handler]
            [clojure.spec.alpha :as s]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [byte-streams :as bs]
            [taoensso.timbre :as log]
            [foundation.spec :as fs]
            [foundation.logging :as fl]
            [foundation.message :as message :refer [format-stream ->transit <-transit]]
            [manifold.stream :as st]
            [clojure.java.io :as io]))

; Config

(def config-filename "config.edn")
(defn load-config
  "Load config file (default `config.edn`) and validate against spec."
  ([spec] (load-config spec config-filename))
  ([spec filename]
   (let [f (io/file filename)]
     (if (.exists f)
       (let [config (->> filename slurp edn/read-string)]
         #_(log/debug "Intepreting config" config "against" spec)
         (if (s/valid? spec config)
           config
           (println "Invalid config" filename ".")))
       (println "No config" filename "found.")))))

; Recaptcha

(defn recaptcha!
  "Verify recaptcha synchronously."
  [secret response]
  @(-> (http/post "https://www.google.com/recaptcha/api/siteverify"
                  {:form-params {:secret secret :response response}})
       (d/chain :body bs/to-string #(json/read-str % :key-fn keyword))
       (d/catch (fn [e] (log/warn "Error verifying reCAPTCHA"
                                  (.getClass e) (.getMessage e))))))

(defn human?
  [{:keys [parameters] :as ctx}
   {:keys [recaptcha-secret] :as config}]
  (let [{:keys [g-recaptcha-response]} (:form parameters)
        {:keys [success score]} (recaptcha! recaptcha-secret g-recaptcha-response)]
    (and success (< 0.5 score))))

; Websocket

(def conform (partial message/conform ::message/->server))
(def validate (partial message/validate ::message/->client))

(def clients "Map of websocket-> nothing yet!" (atom {})) ; TODO ***

(defn ws-send
  "Send `msg` to connected `user`s over their registered websocket/s.
  See `f.common.message` specs."
  [user-msg-map]
  (doseq [[user msg] user-msg-map
          :let [[type & _ :as validated]
                (or (validate msg) [:error :outgoing "Problem generating server reply." msg])
                [ws u] @clients]
          :when (= u user)]
    (if (= type :error) (log/warn "Telling" user "about server error" msg))
    (-> (st/put! ws validated)
        (d/chain #(if-not % (log/info "Failed to send" msg "to" user)
                            #_ (log/debug "Sent" msg "to" user)))
        (d/catch #(log/info "Error sending" msg "to" user %)))))

(defn ws-receive
  "Acknowledge request with appropriate reply."
  [user msg]
  (ws-send (if-let [conformed (conform msg)]
             (message/receive clients user conformed)
             {nil [:error :incoming "Invalid message sent to server." msg]})))

(defn setup-websocket
  "Maintain registry of clients" ; TODO *** and all the rest! see Leavetracker
  ; TODO need to have some idea who's who; could do by ip?
  ; better not to open ws for allcomers, should auth somehow first?
  [ws]
  (let [formatted-ws (format-stream ws ->transit <-transit)
        ws-hash (hash formatted-ws)]
    (log/debug "Preparing websocket" ws-hash)
    (st/on-closed formatted-ws (fn [] (log/debug "Disconnected" ws-hash)
                                      (swap! clients dissoc formatted-ws)))
    (swap! clients assoc formatted-ws :nothing-useful-yet)
    (st/put! formatted-ws [:ready])
    (log/debug "Web socket ready" ws-hash)
    (st/consume (partial ws-receive) formatted-ws)))

(defn websocket-handler [req]
  ; FIXME need to harden this public exposed endpoint
  (-> (http/websocket-connection req)
      (d/chain setup-websocket)
      (d/catch (fn [& args]
                 (log/info "Websocket exception" args)
                 {:status 400
                  :headers {"Content-type" "text/plain"}
                  :body "Expected a websocket request"}))))

; Ajax

(defn ajax-send
  "Send `msg` reply. See `f.common.message` specs."
  [msg]
  (let [[type & _ :as validated]
        (or (validate msg) [:error :outgoing "Problem generating server reply." msg])]
    (if (= type :error) (log/warn "Telling user about server error" msg))
    (->transit validated)))

(defn ajax-receive
  "Acknowledge request with appropriate reply."
  [msg]
  (ajax-send (if-let [conformed (conform msg)]
               (message/receive nil nil conformed)
               [:error :incoming "Invalid message sent to server." msg])))

; Auth

(def failure
  {:produces "application/transit+json"
   :response (fn [ctx] (->transit (-> ctx :error Throwable->map
                                      (select-keys [:cause :data]))))})

#_(def login
    (yada/resource
      {:id :login
       :responses (zipmap [401 403 500] (repeat failure))
       :methods
       {:get
        {:produces "application/transit+json"
         :response
         (fn [ctx]
           (let [{:keys [user] :as auth-map}
                 (get-in ctx [:authentication "default"])]
             ; "default" is realm
             ; value seems to be response from verify:
             ; {:user "username", :roles #{:role}}
             (->transit {:user user :token (auth/write-token auth-map)})))}}}))

; Server

(defn add-nonce [ctx]
  (assoc ctx :nonce (rand-int 1e6)))

(defn add-csp
  "Work around inability to pass function in :content-security-policy."
  [{:keys [nonce] :as ctx}]
  (assoc-in ctx [:response :headers "content-security-policy"]
            ; this is such a mess, possibly "worse" on firefox
            ; https://github.com/google/recaptcha/issues/107
            (str "script-src 'unsafe-eval' 'nonce-" nonce "';")))

#_(def example-routes
    ["/" {"" (server/resource
               {:methods {; TODO deal with GET queries :parameters {:query :spec?}...
                          :get {:produces "text/html"
                                :response :response?}
                          :post {:consumes "application/x-www-form-urlencoded"
                                 :parameters {:form :schema?}
                                 :produces "text/html"
                                 :response :response?}}
                :responses {400 {:produces "text/html"
                                 :response :response?}}})}])

(defn resource
  "Make yada resource with nonce and Content Security Policy."
  [resource-map]
  (-> (yada/resource (assoc resource-map
                       ; not sure why this needs to be added explicitly
                       :interceptor-chain yada/default-interceptor-chain))
      (yada.handler/prepend-interceptor add-nonce)
      (yada.handler/append-interceptor yada.security/security-headers add-csp)))

; Administration

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
   ["-r" "--repl PORT" "Provide nREPL on specified port."
    ; no default because don't provide nREPL by default; app to pull in nrepl dep
    :parse-fn #(Integer/parseInt %)
    :validate [#(s/valid? ::fs/repl %) "Please use port in range 9000-9999."]]
   ["-n" "--dry-run" "Run without doing anything important."]
   ["-l" "--log-level LEVEL" "Set log level."
    :default :info
    :parse-fn keyword
    :validate [#(s/valid? ::fs/log-level %) "Please use debug, info or warn."]]])

(def --allow-origin
  ["-o" "--allow-origin HOST" "Allow api use from sites served at this (single) host."
   :validate [#(s/valid? ::fs/allowed-origin %) "Invalid host."]])

(defn roll-up
  "Roll up relevant cli options into config (default: port and dry-run)."
  [spec {:keys [config] :as options} & additional-keys]
  (log/debug "Rolling up" spec "with" options "and" additional-keys)
  (merge (load-config spec config) (select-keys options (conj additional-keys :port :dry-run))))

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