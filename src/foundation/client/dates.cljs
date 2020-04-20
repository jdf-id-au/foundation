(ns foundation.client.dates
  (:require [oops.core :refer [oget oset!]]
            [tick.alpha.api :as t]
            [tick.format]
            [java.time :refer [DayOfWeek LocalDateTime LocalDate]]
            ["@js-joda/locale_en" :refer [Locale]]
            [foundation.client.logging :as log]))

(defn tick-utils
  "Adapt tick api for @date-io for use by @material-ui/pickers.
   Derived from date-io's moment.utils.ts, date-io_interface.ts and *.test.ts .
   Pickers don't cope well with nil date yet so need to protect them!"
  ; https://github.com/mui-org/material-ui-pickers/issues/1204
  [props]
  (let [; NB I think these are exposed via reify's (:locals &env)
        ; ignore (.-instance props) method of passing in date library
        locale (.-locale props)
        yearFormat "yyyy"
        yearMonthFormat "MMMM yyyy"
        dateTime12hFormat "MMMM d hh:mm a"
        dateTime24hFormat "MMMM d HH:mm"
        time12hFormat "hh:mm a"
        time24hFormat "HH:mm"
        dateFormat "MMMM d"
        ; FIXME ignores locale
        fmt (fn [d formatString]
              (if d (t/format (tick.format/formatter formatString (oget Locale "ENGLISH")) d)
                    ""))
        parse (fn [v]
                (letfn
                  [(report [v label v'] #_(log/info v (str "-" label "->") v') v')]
                  (cond
                    (nil? v) (report  v "nil" nil)
                    (instance? LocalDateTime v) (report v "LocalDateTime" v)
                    (instance? js/Date v) (report v "js/Date" (-> v t/instant t/date-time))
                    (number? v) (report v "number" (-> v js/Date. t/instant t/date-time))
                    (clojure.string/blank? v) (report v "blank" nil)
                    (string? v) (report v "string" (-> v t/parse t/date-time))
                    :else (report v "catchall" v))))
        or-now (fn [d] (or d (t/date-time (t/now))))]
    (log/debug "locale is" locale)
    (reify
      Object
      (date [_ v] (parse v))
      (parse [_ v f] (parse v)) ; FIXME doesn't use format! won't handle craziness

      (isNull [_ d] (nil? d))
      (isValid [_ v] (let [valid? (or (instance? LocalDateTime v)
                                      (instance? LocalDate v)
                                      (try (boolean (parse v)) (catch :default e false)))]
                     ; NB runs three times for bring-up picker and NINE times for submit?!
                       #_(log/info (if valid? "valid: " "invalid: ") v) valid?))
      (getDiff [_ d o] (.until d o (tick.core/unit-map :millis)))
      (isEqual [_ v o] (= v o)) ; (= nil nil) is true
      (isSameDay [_ d o] (and d o (= (t/date d) (t/date o))))

      ; TODO comparators could pre-convert to date to save effort... not going to use times in this project?
      (isAfter [_ d v] (and d v (try (t/> d v)
                                     (catch :default e
                                       ; TODO should be more specific with catch type
                                       (do #_(log/info "converting to date to allow comparison"
                                                       d "isAfter" v e)
                                           (t/> (t/date d) (t/date v)))))))
      (isAfterDay [_ d v] (and d v (t/> (t/date d) (t/date v))))
      (isAfterYear [_ d v] (and d v (t/> (t/year d) (t/year v))))

      (isBefore [_ d v] (and d v (try (t/< d v)
                                      (catch :default e
                                        (do #_(log/info "converting to date to allow comparison"
                                                        d "isBefore" v e)
                                            (t/< (t/date d) (t/date v)))))))
      (isBeforeDay [_ d v] (and d v (t/< (t/date d) (t/date v))))
      (isBeforeYear [_ d v] (and d v (t/< (t/year d) (t/year v))))

      (startOfMonth [_ d] (t/beginning (t/year-month (or-now d))))
      (endOfMonth [_ d] (t/end (t/year-month d)))

      (addDays [_ d c] (t/+ d (t/new-period c :days)))

      (startOfDay [_ d] (t/beginning d))
      (endOfDay [_ d] (t/end d))

      (format [_ d formatString] (fmt d formatString))
      (formatNumber [_ n] n)

      (getHours [_ d] (t/hour d))
      (setHours [_ d c] (t/at (t/date d) (t/new-time c (t/minute d))))

      (getMinutes [_ d] (t/minute d))
      (setMinutes [_ d c] (t/at (t/date d) (t/new-time (t/hour d) c)))

      (getSeconds [_ d] (t/second d))
      (setSeconds [_ d c] (t/at (t/date d) (t/new-time (t/hour d) (t/minute d) c)))

      (getMonth [_ d] (t/month d))
      (setMonth [_ d c] (t/new-date (t/int (t/year d)) c (t/day-of-month d)))
      (getNextMonth [_ d] (t/+ d (t/new-period 1 :months)))
      (getPreviousMonth [_ d] (t/- d (t/new-period 1 :months)))

      (getMonthArray [_ d] (let [start (t/beginning (t/year d))]
                             (clj->js (map #(t/+ start (t/new-period % :months)) (range 12)))))
      (getYear [_ d] (t/year d))
      (setYear [_ d y] (t/new-date y (t/month d) (t/day-of-month d)))

      (mergeDateAndTime [_ d t]
        (if d (if (instance? LocalDateTime t) (t/at (t/date d) (t/time t)) d)))

      (getWeekdays [_] (clj->js (map #(fmt % "E")
                                     ((.-values DayOfWeek)))))
      (getWeekArray [_ d]
        ; Rows of days on a Monday-based calendar, including adjacent months if req.
        (let [start-month (-> d t/year-month t/beginning)
              ; t/end identifies 00:00 the next day as the end of the month, so back up 1d:
              end-month (-> d t/year-month t/end (#(t/- % (t/new-period 1 :days))))
              ; ISO-8601 Monday = 1, Sunday = 7 in java.time
              start-dow-offset (-> start-month t/day-of-week .value dec)
              end-dow-offset (-> end-month t/day-of-week .value dec (#(- 6 %)))
              start (t/- start-month (t/new-period start-dow-offset :days))
              end (t/+ end-month (t/new-period end-dow-offset :days))
              days (.until start end (:days tick.core/unit-map))
              dates (map #(t/+ start (t/new-period % :days)) (range (inc days)))]
          (clj->js (partition-all 7 dates))))
      (getYearRange [_ s e]
        (let [start (t/beginning (t/year s))
              end (t/end (t/year e))
              years (.until start end (:years tick.core/unit-map))]
          (apply array (map #(t/+ start (t/new-period % :years)) (range (inc years))))))

      (getMeridiemText [_ m] (case m "am" "AM" "PM"))
      (getCalendarHeaderText [_ d] (fmt d yearMonthFormat))
      (getDatePickerHeaderText [_ d] (fmt d "E d MMM"))
      (getDateTimePickerHeaderText [_ d] (fmt d "d MMM"))
      (getMonthText [_ d] (fmt d "MMMM"))
      (getDayText [_ d] (fmt d "d"))
      (getHourText [_ d m] (fmt d (if m "hh" "HH")))
      (getMinuteText [_ d] (fmt d "mm"))
      (getSecondText [_ d] (fmt d "ss"))
      (getYearText [_ d] (fmt d "YYYY")))))