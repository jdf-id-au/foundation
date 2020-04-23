(ns foundation.client.logging
  "Direct use of Javascript Console logging.
   ClojureScript data structures render nicely in Chrome DevTools thanks to `binaryage/devtools`."
  (:require [foundation.client.config :refer [config]]))

(def levels
  (let [level (:log-level config)]
    (set (cons level (take-while #(not= level %) [:error :warn :info :debug])))))

(defn level [k f]
  (if (levels k) f (constantly nil)))

(def debug (level :debug js/console.debug))
(def info (level :info js/console.info))
(def warn (level :warn js/console.warn))
(def error (level :error js/console.error))

(defn show [v] (debug v) v)
(defn throw [& args]
  (apply error args)
  (throw (js/Error. (apply str (interpose " " args)))))

(defn table
  ([x] (js/console.table
         (if (or (array? x) (object? x))
           x
           (clj->js x))))
  ([x & xs] (table (cons x xs))))

; TODO group ... groupEnd