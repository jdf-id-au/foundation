(ns foundation.logging
  (:require [taoensso.timbre :as log]
            [taoensso.encore :as enc]
            [clojure.string :as str])
  (:import (java.util TimeZone)))

(defn default-output-fn
  "Simplified from `taoensso.timbre`."
  ([     data] (default-output-fn nil data))
  ([opts data] ; For partials
   (let [{:keys [no-stacktrace?]} opts
         {:keys [level ?err msg_ ?ns-str ?file ?line]} data]
     (str
       (str/upper-case (name level))  " "
       "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err ?err]
           (str enc/system-newline (log/stacktrace err opts))))))))

(def journald-config
  "No timestamp or hostname."
  {:output-fn default-output-fn})

(defn pprint
  "Middleware after https://github.com/ptaoussanis/timbre/issues/184#issuecomment-397421329"
  [data]
  (binding [clojure.pprint/*print-right-margin* 100
            clojure.pprint/*print-miser-width* 50]
    (update data :vargs
      (partial mapv #(if (string? %) % (with-out-str (clojure.pprint/pprint %)))))))

(defn configure
  "Add middleware and config logging with sane defaults."
  [log-level]
  (log/merge-config! {:middleware [pprint]
                      :min-level [[#{"io.netty.*"} :info]
                                  [#{"*"} (or log-level :debug)]]
                      :timestamp-opts {:timezone (TimeZone/getDefault)}}))