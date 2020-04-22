(ns user
  "Dev proof of concept, not a substitute for formal testing."
  (:require [helix.core :refer [defnc $ <>]]
            ["react-dom" :refer [render]]
            [foundation.client.api :as f]
            [foundation.client.config :as config]
            [foundation.time :as time]
            [foundation.client.logging :as log]))

(f/store! {} [{:name "John"}
              {:name "Dev"}])

(f/register :example '[:find [?n ...] :where [?e :name ?n]])

(f/defevent click (fn [co msg] [[:clicked co msg]
                                [:db [{:name (str (rand-int 100))}]]])
                :co)

(defnc app []
  (let [eg-sub (f/subscribe :example)]
    (<> ($ :h1 "jdf/foundation")
        ($ :h2 "v" (:version config/config))
        ($ :div "debug mode: " (if config/debug? "on" "off"))
        ($ :div "config from html: " (:from_html config/config))
        ($ :div "subscription:")
        ($ :ul
          (for [s eg-sub]
            ($ :li {:key s} s)))
        ($ :div (time/format "hh:mm dd MMM yyyy" (time/now)))
        ($ :button {:on-click #(click "argument")} "click me"))))

(defn ^:dev/after-load start-up []
  (f/start! {:root-component app
             :coeffects {:co [str "coeffect"]
                         :now [time/now]}
             :effects {:clicked println}}))

(defn init []
  (start-up))

