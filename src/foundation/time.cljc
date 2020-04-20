(ns foundation.time
  (:require [tick.alpha.api :as t]
            [tick.core]
            [tick.format]
            #?@(:clj [[clojure.spec.alpha :as s]
                      [clojure.spec.gen.alpha :as gen]]
                :cljs [[cljs.spec.alpha :as s]
                       [cljs.spec.gen.alpha :as gen]
                       [clojure.test.check.generators]
                       [foundation.client.logging :as log]
                       [java.time :refer [LocalDate LocalDateTime DayOfWeek Month]]
                       ["@js-joda/locale_en" :refer [Locale]]
                       [oops.core :refer [oget]]]))
  (:import #?@(:clj [[java.time LocalDate LocalDateTime DayOfWeek Month]
                     (java.util Locale)]))
  (:refer-clojure :exclude [format]))

(def new-date t/new-date)
(def date t/date)
(defn date-time
  "Change space in `yyyy-MM-DD HH:MM...` to T, then convert.
   For converting sqlite `text default current_timestamp`s." ; TODO could format in sqlite...
  [s] (if s (t/date-time (apply str (-> s vec (assoc 10 "T"))))))
(defn format [f d]
  ; FIXME handle locale
  (t/format (tick.format/formatter f
              #?(:clj Locale/ENGLISH
                 :cljs (oget Locale "ENGLISH"))) d))

(defn this-year [] (t/int (t/year)))
(defn today [] (new-date))
(defn now [] (t/at (new-date) (t/new-time)))

(def month-abbr (partial format "MMM"))
#_(def days (map #(t/format (tick.format/formatter "EEEE") %)
              #?(:clj (DayOfWeek/values) :cljs ((.-values DayOfWeek)))))
#_(def months (map #(t/format (tick.format/formatter "MMMM") %)
                #?(:cljs (Month/values) :cljs ((.-values Month)))))

(defn date-range-text [[start end]]
  (let [expand (juxt t/year t/month t/day-of-month)
        [y1 m1 d1] (expand start)
        [y2 m2 d2] (expand end)
        mn1 (month-abbr start)
        mn2 (month-abbr end)
        -- "–" ; this is an en dash
        _ " "]
    (cond
      (= [y1 m1 d1] [y2 m2 d2]) (str d1 _ mn1 _ y1)
      (= [y1 m1] [y2 m2]) (str d1 -- d2 _ mn2 _ y2)
      (= y1 y2) (str d1 _ mn1 -- d2 _ mn2 _ y2)
      :else (str d1 _ mn1 _ y1 -- d2 _ mn2 _ y2))))

(def before? t/<)

(defn within?
  "Is the first date (or date pair) within the second date pair? Accommodate nil in pair.
   In Allen's interval algebra terminology: first 'during or equal or starts or finishes' second.
   `exclude-outer-dates?` requires first to be within-and-not-touching the second.
   In Allen's terminology: first 'during' second."
  ([inner [outer-start outer-end]] (within? inner [outer-start outer-end] false))
  ([inner [outer-start outer-end] exclude-outer-dates?]
   (let [[inner-start inner-end] (if (vector? inner) inner [inner inner])
         cmp (if exclude-outer-dates? t/< t/<=)]
     (if outer-start
       (if outer-end
         (and inner-start
              inner-end
              (cmp outer-start inner-start)
              (cmp inner-end outer-end))
         ; no outer-end
         (and inner-start
              (cmp outer-start inner-start)))
       ; no outer-start
       (if outer-end
         (and inner-end
              (cmp inner-end outer-end))
         ; no outer-start or outer-end
         true)))))

(defn overlaps?
  "Do the two date pairs overlap (including having a date in common)? Accommodate nil in pair."
  ; Readability over concision!
  ; Nicer to do like this than calling libraries, plus they don't accommodate nil dates.
  [[start-a end-a] [start-b end-b]]
  (if start-a
    (if end-a
      (if start-b
        (if end-b
          (and (t/<= start-b end-a)
               (t/<= start-a end-b))
          ; no end-b
          (t/<= start-b end-a))
        ; no start-b
        (if end-b
          (t/<= start-a end-b)
          ; no start-b or end-b
          true))
      ; no end-a
      (if end-b
        (t/<= start-a end-b)
        ; no end-a or end-b
        true))
    ; no start-a
    (if end-a
      (if start-b
        (t/<= start-b end-a)
        ; no start-a or start-b
        true)
      ; no start-a or end-a
      true)))

(defn in-days "Duration in days." [d1 d2] (.until d1 d2 (tick.core/unit-map :days)))

(defn duration
  "Number of days spanned by date pair (inclusive)."
  [date-pair] (some->> date-pair (apply in-days) inc))

(defn overlapping?
  "Do any of the supplied pairs of dates overlap? Defaults to excluding [nil nil] pairs."
  ([date-pairs] (overlapping? date-pairs false))
  ([date-pairs include-nil-pairs?]
   (let [nilfn (if include-nil-pairs? identity (partial remove #(= [nil nil] %)))]
     ; NB if date-pairs only contains one pair, reduce will return it, which will not be `true?`
     (true? (->> date-pairs nilfn sort ; TODO check sorting with tick date pairs
                 (reduce
                   (fn ([] false)
                       ([pair-a pair-b]
                        (if (overlaps? pair-a pair-b)
                          (reduced true)
                          pair-b)))))))))

(defn earliest [& ds]
  (reduce (fn ([])
              ([d1 d2] (if (before? d1 d2) d1 d2)))
          (remove nil? ds)))

(defn latest [& ds]
  (reduce (fn ([])
              ([d1 d2] (if (before? d1 d2) d2 d1)))
          (remove nil? ds)))

(defn date-bound [bound field [start end]]
  (let [lower (t/- (t/new-date) (t/new-period 1 :years))
        upper (t/+ (t/new-date) (t/new-period 10 :years))]
    (case bound
      :min (case field :start (earliest lower start) :end (or start lower))
      :max (case field :start (or end upper) :end (latest upper end)))))

; Spec

(defn minutes-away
  ([n from] (t/+ from (t/new-duration n :minutes)))
  ([n] (minutes-away n (now))))
(defn days-away
  ([n from] (t/+ from (t/new-period n :days)))
  ([n] (days-away n (today))))
(def day-after (partial days-away 1))
(def day-before (partial days-away -1))
(defn years-away
  ([n from] (t/+ from (t/new-period n :years)))
  ([n] (years-away n (today))))
(s/def ::date
  (s/with-gen #(instance? LocalDate %)
              #(gen/fmap days-away (gen/large-integer* {:min -50 :max +300}))))
(s/def ::datetime
  (s/with-gen #(instance? LocalDateTime %)
              #(gen/fmap minutes-away (gen/large-integer* {:min (* 24 -60) :max (* 24 60)}))))

(defn pair [i] (vec (repeat 2 i)))
(s/def ::single (s/with-gen (s/and (s/tuple ::date ::date) #(apply = %))
                            #(gen/fmap pair (s/gen ::date))))
(s/def ::closed (s/and (s/tuple ::date ::date) #(not (apply t/> %))))
(s/def ::no-end (s/tuple ::date nil?))
(s/def ::no-start (s/tuple nil? ::date))
(s/def ::open (s/tuple nil? nil?))
(s/def ::flexible (s/or :single ::single ; has to come first as is also `closed`
                        :closed ::closed
                        :no-end ::no-end
                        :no-start ::no-start
                        :open ::open))