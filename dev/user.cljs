(ns user
  "Dev proof of concept, not a substitute for formal testing.
   Presented at http://localhost:8888/index.html (see shadow-cljs.edn)"
  (:require [helix.core :refer [defnc $ <>]]
            ["react-dom" :refer [render]]
            [common]
            [foundation.client.api :as f]
            [foundation.client.config :as config]
            [tick.alpha.api :as t]
            [temper.api :as tm]
            [foundation.client.logging :as log]
            [foundation.message :as message]))

; Sets up datascript, so call even if nothing to store yet.
#_ (f/store! {} {})
(f/store! {} [{:name "John"}
              {:name "Dev"}])

(f/register :example '[:find [?n ...] :where [?e :name ?n]])

(f/defevent click (fn [co msg] [[:clicked co msg]
                                [:db [{:name (str (rand-int 100))}]]])
                :co)

(f/defevent ping (fn [msg] [[:post "hello" [:ping :hello "there"]]]))

(defmethod message/receive :pong [msg]
  (log/debug "Received" msg))

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
        ($ :div (tm/format "HH:mm dd MMM yyyy" (tm/now)))
        ($ :button {:on-click #(click "argument")} "click me")
        ($ :button {:on-click #(ping "server?")} "ping server")
        ($ :img {:src "background.png"}))))

(defn ^:dev/after-load start-up []
  (f/start! {:root-component app
             :coeffects {:co [str "coeffect"]
                         :now [tm/now]}
             :effects {:clicked println}}))

(defn ^:export init []
  (start-up))