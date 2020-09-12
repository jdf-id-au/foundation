(ns foundation.spec
  "yada :parameters uses plumatic/schema, we use clojure.spec everywhere else."
  (:require #?@(:clj  [[clojure.spec.alpha :as s]
                       [clojure.spec.gen.alpha :as gen]
                       [clojure.java.io :as io]]
                :cljs [[cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]])
            [comfort.spec :as cs])
  #?(:clj (:import (java.io File))))

#?(:clj (s/def ::config-file (s/and #(clojure.string/ends-with? ".edn" %)
                                    #(.exists (io/as-file %)))))

(s/def ::allowed-origin (s/and string?
                               #(let [{:keys [scheme host port path]} (cs/URI-parts %)]
                                  (and scheme host (not path)))))
(s/def ::log-level #{:debug :info :warn})
(s/def ::recaptcha-key ::cs/non-blank-string)
(s/def ::recaptcha-secret ::cs/non-blank-string)

(s/def ::port (s/int-in 8000 9000))
(s/def ::repl (s/int-in 9000 10000))

(s/def :api/tls boolean?)
(s/def :api/host string?)
(s/def :api/port ::port)
(s/def :client/dev (s/keys :opt-un [:api/tls :api/host :api/port ::log-level]))
(s/def ::client-config (s/keys :opt-un [:client/dev ; contents promoted to root in goog.DEBUG mode
                                        :api/tls :api/host :api/port ::log-level]))

#_(s/def ::config (s/keys :req-un [::recaptcha-key ::recaptcha-secret]))