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

(deftest duration
  (let [d1 (t/new-date 2018 1 1)
        d2 (t/new-date 2018 1 8)
        d3 (t/new-date 2019 9 21)
        d4 (t/new-date 2019 10 20)]
    (testing "Duration calculation"
      (is (= (t/in-days d1 d2) 7))
      (is (= (t/in-days d1 d1) 0))
      (is (= (t/in-days d2 d1) -7))
      (is (= (t/in-days d3 d4) 29)))
    (testing "Date range text"
      (is (= "1–8 Jan 2018" (t/date-range-text [d1 d2]))))))