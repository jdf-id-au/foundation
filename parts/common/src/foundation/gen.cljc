(ns foundation.gen
  "Support generative testing. Get gen-related noise out of other namespaces."
  (:require #?@(:clj [[clojure.spec.alpha :as s]
                      [clojure.spec.gen.alpha :as gen]
                      [clojure.java.io :as io]]
                :cljs [[cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]
                       [clojure.test.check.generators]]))
  #?(:cljs (:require-macros [foundation.gen :refer [dict]]))
  (:refer-clojure :exclude [name comment]))

#?(:clj (def words
          (->> (io/resource "alice.txt")
               slurp
               (re-find #"(?s)\*\*\* START.*?\*\*\*(.*?)\*\*\* END")
               second
               (#(-> % clojure.string/lower-case
                     (clojure.string/replace #"[^a-z']" " ")
                     (clojure.string/split #"\s+")))
               (filter #(< 3 (count %)))
               set)))
#?(:clj (defmacro dict [] `~words))
(s/def ::word (dict))

(defn name [] (s/gen ::word))
(defn comment []
  (gen/fmap (fn [ws] (->> ws (interpose " ") (apply str)))
            (s/gen (s/coll-of ::word))))

(defn retag
  "Allow proper generation of multi-spec data.
  Tag is really the dispatch-tag, not the assigned tag,
  so ignore it rather than (assoc gen-v :state-etc tag)."
  [gen-v tag] gen-v)

(defn one [spec] (gen/generate (s/gen spec)))
(defn n [spec n] (gen/sample (s/gen spec) n))
(def exercise s/exercise)