(ns foundation.logging)

(defn pprint-middleware
  "After https://github.com/ptaoussanis/timbre/issues/184#issuecomment-397421329"
  [data]
  (binding [clojure.pprint/*print-right-margin* 100
            clojure.pprint/*print-miser-width* 50]
    (update data :vargs
      (partial mapv #(if (string? %) % (with-out-str (clojure.pprint/pprint %)))))))

(def not-daemon (partial filter #(false? (:daemon %))))

(defn print-threads
  "After https://gist.github.com/DayoOliyide/f353b15563675120e408b6b9f504628a
   `(print-threads nil identity)` for full report."
  ([] (print-threads [:name :state :alive :daemon]))
  ([headers] (print-threads headers not-daemon))
  ([headers pre-fn]
   (let [thread-set (keys (Thread/getAllStackTraces))
         thread-data (mapv bean thread-set)
         headers (or headers (-> thread-data first keys))]
     (clojure.pprint/print-table headers (pre-fn thread-data)))))

(defn print-threads-str [& args]
  (with-out-str (apply print-threads args))) ()