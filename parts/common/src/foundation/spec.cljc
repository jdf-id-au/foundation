(ns foundation.spec
  (:require #?@(:clj  [[clojure.spec.alpha :as s]
                       [clojure.spec.gen.alpha :as gen]
                       [clojure.java.io :as io]]
                :cljs [[cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]])
            [comfort.spec :as cs])
  #?(:clj (:import (java.io File)
                   (java.net URL))))

#?@(:clj [(s/def ::directory #(-> % io/file .isDirectory))
          (s/def ::file #(-> % io/file .isFile))
          (s/def ::resource #(-> % io/resource io/file .isFile)) ; ~dev convenience
          (s/def ::url #(try (URL. %) (catch Exception _)))
          (s/def ::file-or-url (s/or :file ::file :resource ::resource :url ::url))

          (s/def ::config-file (s/and #(clojure.string/ends-with? % ".edn")
                                 #(.exists (io/as-file %))))])

(s/def ::allowed-origin
  (s/and string?
         #(let [{:keys [scheme host port path]} (cs/URI-parts %)]
            ; https://developers.google.com/web/updates/2020/07/referrer-policy-new-chrome-default#what_does_this_change_mean
            (and scheme host #_(not path))))) ; trying to work out strict-origin-when-cross-origin
(s/def ::log-level #{:debug :info :warn})
(s/def ::recaptcha-key ::cs/non-blank-string)
(s/def ::recaptcha-secret ::cs/non-blank-string)

(s/def ::port (s/int-in 8000 9000))
(s/def ::repl (s/int-in 9000 10000))

(s/def :api/tls boolean?)
(s/def :api/host string?)
(s/def :api/port ::port)
(s/def :client/dev (s/keys :opt-un [:api/tls :api/host :api/port ::log-level]))
(s/def ::client-config
  (s/keys :opt-un [; dev contents promoted to root in goog.DEBUG mode
                   :client/dev
                   :api/tls :api/host :api/port ::log-level]))

(s/def ::config (s/keys :opt-un [::recaptcha-key ::recaptcha-secret]))
