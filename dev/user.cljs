(ns user
  "Dev proof of concept, not a substitute for formal testing.
   Presented at http://localhost:8888/index.html (see shadow-cljs.edn)"
  (:require [helix.core :refer [defnc $ <>]]
            [common] ; dev/common.cljc
            [foundation.client.api :as f]
            [foundation.client.config :as config]
            [temper.api :as tm]
            [foundation.client.logging :as log]
            [foundation.message :as message]))

(f/register :example '[:find [?n ...] :where [?e :name ?n]])

(f/defevent click (fn [co msg] [[:clicked co msg]
                                [:db [{:name (str msg (rand-int 100))}]]])
                :co)

(f/defevent ping (fn [msg] [[:post "hello" [:ping :hello msg]]]))

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
        ($ :div (tm/format "dd/MM/yyyy HH:mm:ss" (tm/now)))
        ($ :button {:on-click #(click "argument")} "click me")
        ($ :button {:on-click #(ping "server?")} "ping server"))))

(defn ^:dev/after-load start []
  (f/render! app))

(defn ^:export init []
  ;; Sets up datascript, so call even if nothing to store yet.
  (f/store! {} [{:name "John"}
                {:name "Dev"}])
  (f/init! {:coeffects {:co [str "coeffect"]
                        :now [tm/now]}
            :effects {:clicked println}}))
