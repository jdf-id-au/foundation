(ns foundation.schema
  "yada defaults to Plumatic Schema rather than clojure spec"
  (:require [schema.core :as s]))

(def Email #"^\S+@\S+\.\S{2,}")
(def NamedEmail #"^[^<>]+ <\S+@\S+\.\S{2,}>")

(def Config
  {:recaptcha-key s/Str
   :recaptcha-secret s/Str})