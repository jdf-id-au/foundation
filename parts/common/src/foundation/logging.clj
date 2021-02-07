(ns foundation.common.logging
  (:require [taoensso.timbre :as log])
  (:import (java.util TimeZone)))

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
                                  [#{"*"} log-level]]
                      :timestamp-opts {:timezone (TimeZone/getDefault)}}))