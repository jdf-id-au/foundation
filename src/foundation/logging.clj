(ns foundation.logging
  (:require [taoensso.encore :as encore]
            [taoensso.timbre :as log])
  (:import (java.util TimeZone)))

(defn ns-filter [fltr] (-> fltr encore/compile-ns-filter encore/memoize_))
(defn ns-pattern-level
  "Middleware after https://github.com/yonatane/timbre-ns-pattern-level"
  [ns-patterns]
  (fn log-by-ns-pattern [{:keys [?ns-str config level] :as opts}]
    (let [namesp (or (some->> ns-patterns
                              keys
                              (filter #(and (string? %)
                                            ((ns-filter %) ?ns-str)))
                              not-empty
                              (apply max-key count))
                     :all)
          loglevel (get ns-patterns namesp (get config :level))]
      (when (and (log/may-log? loglevel namesp)
                 (log/level>= level loglevel))
        opts))))

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
  (log/merge-config! {:middleware [pprint
                                   (ns-pattern-level {"io.netty.*" :info
                                                      :all log-level})]
                      :timestamp-opts {:timezone (TimeZone/getDefault)}}))