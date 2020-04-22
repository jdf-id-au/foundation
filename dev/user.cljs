(ns user
  (:require [helix.core :refer [defnc $ <>]]
            ["react-dom" :refer [render]]
            [foundation.client.state :as state]
            [foundation.client.config :as config]
            [foundation.time :as time]
            [foundation.client.logging :as log]))

(state/store! {} [{:name "someone"}
                  {:name "anotherone"}])

(state/register :example '[:find [?n ...] :where [?e :name ?n]])

;(state/defevent click (fn [co1] [:update :this co1]) "co1")

(defnc app []
  (let [eg-sub (state/subscribe :example)]
    (<> ($ :h1 "jdf/foundation")
        ($ :h2 "v" (:version config/config))
        ($ :div "debug mode: " (if config/debug? "on" "off"))
        ($ :div "config from html: " (:from_html config/config))
        ($ :div "subscription:")
        ($ :ul
          (for [s eg-sub]
            ($ :li s)))
        ($ :div (time/format "hh:mm dd MMM yyyy" (time/now)))
        ($ :button {:on-click #()}))))

(defn register-effects! []
  (swap! state/cofx assoc
    :now [time/now]))


(defn ^:dev/after-load mount-root []
  (render ($ app) (. js/document getElementById "app")))

(defn init []
  (register-effects!)
  (mount-root))