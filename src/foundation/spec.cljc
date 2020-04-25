(ns foundation.spec
  "yada :parameters uses plumatic/schema, we use clojure.spec everywhere else."
  (:require #?@(:clj  [[clojure.spec.alpha :as s]
                       [clojure.spec.gen.alpha :as gen]]
                :cljs [[cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]])
            [clojure.java.io :as io])
  (:import (java.io File)))

; Suitable for use in schema or spec:
(def URI #"^(https?)://([^/:]*):?(\d+)?(/.*)?")
(def Email #"^\S+@\S+\.\S{2,}")
(def NamedEmail #"^[^<>]+ <\S+@\S+\.\S{2,}>")

(s/def ::non-blank-string (s/and string? #(-> % clojure.string/blank? not)))

#?(:clj (s/def ::config-file (s/and #(clojure.string/ends-with? ".edn" %)
                                    #(.exists (io/as-file %)))))
(s/def ::allowed-origin (s/and string? (fn [host]
                                         (let [[_ scheme host port path]
                                               (->> host (re-seq URI) first)]
                                           (and scheme host (not path) true)))))
(s/def ::log-level #{:debug :info :warn})
(s/def ::recaptcha-key string?)
(s/def ::recaptcha-secret string?)

(s/def ::uri (s/and string? #(re-matches URI %))) ; better if conformed to something? or used existing parser?
(s/def ::port (s/int-in 8000 9000))
(s/def ::email (s/and string? #(re-matches Email %)))
(s/def ::named-email (s/and string? #(re-matches NamedEmail %)))

#_(s/def ::config (s/keys :req-un [::recaptcha-key ::recaptcha-secret]))