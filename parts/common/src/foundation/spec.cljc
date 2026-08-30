(ns foundation.spec
  (:require #?@(:clj  [[clojure.spec.alpha :as s]
                       [clojure.spec.gen.alpha :as gen]
                       [clojure.java.io :as io]]
                :cljs [[cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]])
            [comfort.spec :as cs])
  #?(:clj (:import (java.io File)
                   (java.net URL))))

;; Reader conditional splicing not allowed at the top level.
#?(:clj (do (s/def ::directory #(-> % io/file .isDirectory))
            (s/def ::file #(-> % io/file .isFile))
            ;; FIXME make work with resources in built jar (avoid IAE: not a file jar:file:/.../blah.jar!resource.ext)
            #_(s/def ::resource #(-> % io/resource io/file .isFile)) ; ~dev convenience
            (s/def ::url #(try (URL. %) (catch Exception _)))
            (s/def ::file-or-url (s/or :file ::file #_#_ :resource ::resource :url ::url))

            (s/def ::config-file (s/and #(clojure.string/ends-with? % ".edn")
                                   #(.exists (io/as-file %))))))

(s/def ::allow-origin ; goes in server opts rather than ctx; doesn't need to be dynamic
  (s/and string?
         #(let [{:keys [scheme host port path]} (cs/URI-parts %)]
            ; https://developers.google.com/web/updates/2020/07/referrer-policy-new-chrome-default#what_does_this_change_mean
            (and scheme host #_(not path))))) ; trying to work out strict-origin-when-cross-origin
(s/def ::log-level #{:debug :info :warn})
(s/def ::recaptcha-key ::cs/non-blank-string)
(s/def ::recaptcha-secret ::cs/non-blank-string)

(s/def ::port integer?) ; TODO 2026-08-30 19:20:05 better validation
(s/def ::repl ::port)
(s/def ::root (s/and string? #(re-matches #"/(.*/)?" %))) ; i.e. starts and ends with /

(s/def ::tls boolean?)
(s/def ::host (s/or :name string? :code #{:site-local}))
(s/def ::origin-port ::port) ; intended for server only, connections from origin port
(s/def ::client-root ::root)

(s/def ::dev (s/keys :opt-un [::tls ::host ::port ::root ::log-level]))

(s/def ::client-config ; see `foundation.client.config`
  (s/keys
    :opt-un [::tls ::host ::port ::root ::log-level
             ;; dev contents promoted to root in goog.DEBUG mode by `foundation.client.config/configure`
             ::dev]))

(s/def ::config (s/keys :opt-un [::recaptcha-key ::recaptcha-secret]))
