(ns foundation.time-test
  (:require #?@(:clj [[clojure.test :refer :all]
                      [clojure.spec.alpha :as s]
                      [clojure.spec.gen.alpha :as gen]]
                :cljs [[cljs.test :refer-macros [deftest is testing]]
                       [cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]])

            [foundation.time :as t]))

(def ds
  "Vector of unique dates, sorted ascending."
  (->> (repeatedly #(gen/generate (s/gen ::t/date)))
       (take 10) ; less repetition this way than when gen/sample starts up
       sort
       dedupe
       vec))

(defn eg
  "Convert three-character keywords like :<-3 to date pairs like [nil (ds 3)]"
  [code-kw]
  (let [[from _ to] (name code-kw)
        int #(-> % str #?(:clj Integer. :cljs js/Number))
        conv #(case % (\< \>) nil
                      (ds (int %)))]
    (vec (map conv [from to]))))

(deftest within
  (is (t/within? (ds 1) (eg :0-3)))
  (is (t/within? (eg :1-2) (eg :0-3)))
  (is (t/within? (eg :1-2) (eg :0->)))
  (is (t/within? (eg :1-2) (eg :1-2))
      "Interval is 'within' itself, i.e. bounds inclusive.")
  (is (not (t/within? (eg :1-2) (eg :1-2) true))
      "...unless explicitly request to exclude outer dates.")
  (is (t/within? (eg :1-2) (eg :<->)))
  (is (t/within? (eg :<-2) (eg :<->)))
  (is (not (t/within? (eg :2-4) (eg :0-3))))
  (is (not (t/within? (eg :0-3) (eg :1-2))))
  (is (not (t/within? (eg :1->) (eg :0-3)))))

(deftest overlapping
  (is (not (t/overlapping? [(eg :1-2) (eg :3-4)])))
  (is (not (t/overlapping? [(eg :<-2) (eg :3-4)])))
  (is (t/overlapping? [(eg :1-2) (eg :2-4)]))
  (is (t/overlapping? [(eg :0->) (eg :1-2)])))

(deftest earliest-latest
  (is (= (ds 0) (t/earliest (ds 1) (ds 0) nil (ds 2))))
  (is (= (ds 2) (t/latest (ds 1) (ds 0) nil (ds 2))))
  (is (nil? (t/latest nil nil))))