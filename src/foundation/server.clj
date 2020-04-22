(ns foundation.server
  "Common basic functionality for web applications."
  (:require [yada.yada :as yada]
            [yada.handler]
            [schema.core :as s]
            [aleph.http :as http]
            [manifold.deferred :as d]
            [clojure.tools.cli :as cli]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [byte-streams :as bs]
            [clojure.java.io :as io]))

; Config

(def config-filename "config.edn")
(defn load-config
  "Load config file (default `config.edn`) and validate against schema."
  ([schema] (load-config schema config-filename))
  ([schema filename] (->> filename slurp edn/read-string (s/validate schema))))

; Server

(defn recaptcha!
  "Verify recaptcha synchronously."
  [secret response]
  @(-> (http/post "https://www.google.com/recaptcha/api/siteverify"
                  {:form-params {:secret secret :response response}})
       (d/chain :body bs/to-string #(json/read-str % :key-fn keyword))
       (d/catch (fn [e] (println "Error verifying reCAPTCHA"
                                 (.getClass e) (.getMessage e))))))

(defn human?
  [{:keys [parameters] :as ctx}
   {:keys [recaptcha-secret] :as config}]
  (let [{:keys [g-recaptcha-response]} (:form parameters)
        {:keys [success score]} (recaptcha! recaptcha-secret g-recaptcha-response)]
    (and success (< 0.5 score))))

(defn add-nonce [ctx]
  (assoc ctx :nonce (rand-int 1e6)))

(defn add-csp
  "Work around inability to pass function in :content-security-policy."
  [{:keys [nonce] :as ctx}]
  (assoc-in ctx [:response :headers "content-security-policy"]
            ; this is such a mess, possibly "worse" on firefox
            ; https://github.com/google/recaptcha/issues/107
            (str "script-src 'unsafe-eval' 'nonce-" nonce "';")))

#_(def resource-template
    {:methods {; TODO deal with GET queries :parameters {:query :spec?}...
               :get {:produces "text/html"
                     :response :response?}
               :post {:consumes "application/x-www-form-urlencoded"
                      :parameters {:form :spec?}
                      :produces "text/html"
                      :response :response?}}
     :responses {400 {:produces "text/html"
                      :response :response?}}})

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
    :validate [#(.exists (io/as-file %)) "No such config file."]]
   ["-p" "--port PORT" "Use specified port."
    :default 8000
    :parse-fn #(Integer/parseInt %)
    :validate [#(<= 8000 % 8999) "Please use port in range 8000-8999."]]
   ["-n" "--dry-run" "Run without doing anything important."]])

(defn roll-up
  "Roll up relevant cli options into config (default: port and dry-run)."
  [schema {:keys [config] :as options} & keys]
  (merge (load-config schema config) (select-keys options (conj keys :port :dry-run))))

(defn validate-args
  [desc cli-options args]
  (let [{:keys [options arguments summary errors]} (cli/parse-opts args cli-options)
        {:keys [help]} options
        summary (str desc \newline summary)]
    (cond errors {:exit [1 (apply str (interpose \newline (cons summary errors)))]}
          help {:exit [0 summary]}
          :else {:options options :arguments arguments})))

(defn exit
  [exit-code exit-message]
  (println exit-message)
  (System/exit exit-code))

(defn cli
  "Call from `-main`. Either exits or returns map of options and arguments."
  [desc cli-options args]
  (let [{exit-args :exit :as validated} (validate-args desc cli-options args)]
    (if exit-args (apply exit exit-args) validated)))