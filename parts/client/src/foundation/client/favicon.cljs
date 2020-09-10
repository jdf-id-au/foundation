(ns foundation.client.favicon
  (:require [foundation.client.logging :as log]))

(defonce favicon (atom nil))

(defn insert!
  "Currently just used to indicate test status, but could be used differently in prod."
  []
  (if-let [el (.getElementById js/document "favicon")]
    (reset! favicon el)
    (let [el (reset! favicon (.createElement js/document "link"))
          head (aget (.getElementsByTagName js/document "head") 0)]
      (.appendChild head el)
      (set! (.-type el) "image/png")
      (set! (.-rel el) "shortcut icon")
      (set! (.-id el) "favicon"))))

(defn colour-data-url [colour]
  (let [cvs (.createElement js/document "canvas")]
    (set! (.-width cvs) 16)
    (set! (.-height cvs) 16)
    (let [ctx (.getContext cvs "2d")]
      (set! (.-fillStyle ctx) colour)
      (.fillRect ctx 0 0 16 16)
     (.toDataURL cvs))))

(defn set-colour! [colour]
  (let [icon (.getElementById js/document "favicon")]
    (try (set! (.-href icon) (colour-data-url colour))
         (catch js/TypeError e
           (log/error "No favicon to set colour!")))))
(defn grey! [] (set-colour! "#9e9e9e"))
(defn red! [] (set-colour! "#f44336"))
(defn orange! [] (set-colour! "#ff9800"))
(defn green! [] (set-colour! "#4caf50"))