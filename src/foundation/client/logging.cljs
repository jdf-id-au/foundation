(ns foundation.client.logging
  "Direct use of Javascript Console logging.
   ClojureScript data structures render nicely in Chrome DevTools thanks to `binaryage/devtools`.")

(def debug js/console.debug)
(def info js/console.info)
(def warn js/console.warn)
(def error js/console.error)
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