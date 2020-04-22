(ns user
  (:require [helix.core :refer [defnc $ <>]]
            ["react-dom" :refer [render]]
            [foundation.client.state :as state]
            [foundation.client.config :refer [config]]
            [foundation.time :as t]
            [foundation.client.logging :as log]))


(defnc app []
  (<> ($ :h1 "jdf/foundation")
      ($ :h2 "v" (:version config))
      ($ :div (t/format "hh:mm dd MMM yyyy" (t/now)))))

(defn register-effects! [])

(defn ^:dev/after-load mount-root []
  (render ($ app) (. js/document getElementById "app")))

(defn init []
  (register-effects!)
  (mount-root))