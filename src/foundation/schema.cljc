(ns foundation.schema
  "yada defaults to Plumatic Schema rather than clojure spec"
  (:require [schema.core :as s]))

(def URI #"^(https?)://([^/:]*):?(\d+)?(/.*)?")
(def Email #"^\S+@\S+\.\S{2,}")
(def NamedEmail #"^[^<>]+ <\S+@\S+\.\S{2,}>")
(def NonBlankString (s/constrained s/Str (comp not clojure.string/blank?)))
(def PositiveInt (s/constrained s/Int pos?))

(def Config
  {:recaptcha-key s/Str
   :recaptcha-secret s/Str})