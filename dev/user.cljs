(ns user
  (:require [helix.core :refer [defnc $ <>]]
            ["react-dom" :refer [render]]
            [foundation.client.state :as state]
            [foundation.client.config :as config]
            [foundation.time :as time]
            [foundation.client.logging :as log]))

(state/store! {} [{:name "John"}
                  {:name "Dev"}])

(state/register :example '[:find [?n ...] :where [?e :name ?n]])

(state/defevent click (fn [co msg] [[:clicked co msg]]) :co)

(defnc app []
  (let [eg-sub (state/subscribe :example)]
    (<> ($ :h1 "jdf/foundation")
        ($ :h2 "v" (:version config/config))
        ($ :div "debug mode: " (if config/debug? "on" "off"))
        ($ :div "config from html: " (:from_html config/config))
        ($ :div "subscription:")
        ($ :ul
          (for [s eg-sub] ; TODO add keys for React
            ($ :li s)))
        ($ :div (time/format "hh:mm dd MMM yyyy" (time/now)))
        ($ :button {:on-click #(click "argument")} "click me"))))

(defn ^:dev/after-load start-up []
  (state/start!
    {:co [str "coeffect"]
     :now [time/now]}
    {:clicked println}
    app "app"))

(defn init []
  (start-up))

