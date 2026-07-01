(ns user
  "Dev proof of concept, not a substitute for formal testing.
   Presented at http://localhost:8888/index.html (see shadow-cljs.edn)"
  (:require [datascript.core :as ds]
            [replicant.dom :as r]
            [nexus.registry :as nxr]
            [common] ; dev/common.cljc
            [foundation.client.api :as f]
            [foundation.client.config :as config]
            [temper.api :as tm]
            [foundation.client.logging :as log]
            [foundation.client.history :as history]
            [foundation.client.default :as default]
            [foundation.message :as message]
            [foundation.client.connection :as connection]))

;; TODO 2026-06-29 11:52:26 think about architecture, clean up rest of foundation.client

;; nxr/register-interceptor! for render lock etc https://github.com/cjohansen/nexus/blob/main/Readme.md#rendering
(nxr/register-placeholder! ::now (fn [] (js/Date.))) ; placeholders need to be "called" from "function position" within a vector
(nxr/register-placeholder! ::rand-int (fn [] (rand-int 100)))

(defn render-page [db]
  (let [misc (ds/entity db [:app/state :misc])
        ui (ds/entity db [:app/state :ui])
        bag (ds/q '[:find [?v ...] :where [_ :thing ?v]] db)]
    [:div
     [:h1 "jdf/foundation"]
     [:h2 "v" (:version config/config)]
     [:div "debug mode: " (if config/debug? "on" "off")]
     [:div "config from html: " (:from_html config/config)]
     [:p "started at: " (:started-at misc)]
     [:button {:on {:click [[::f/db [{:thing [::rand-int]}]]]}} "random number please"]
     [:p "view: " (:view ui) " route-params: " (:route-params ui)]
     [:ul (for [i bag] [:li i])]
     [:button {:on {:click [[::f/navigate [:home]]]}} "home"]
     [:button {:on {:click [[::f/navigate [:example nil]]]}} "example"] ; route-params would be in place of nil
     [:button {:on {:click [[::f/db [{:app/state :misc :started-at [::now]}]]]}}
      "fib about time"]
     [:button {:on {:click [[::f/post ["hello" [:ping :hello "message from button"]]]]}} ; dev/common.cljc message :ping
      "ping server"]
     ]))

(defmethod message/receive :pong [msg]
  (log/info "Received" msg)
  (nxr/dispatch (f/conn) nil [[::f/db [{:thing (:word msg)}]]]))

(def routes ; see foundation.client.default/routes
  ["" [["/" {"" :home
             "example" :example
             "not-found" :not-found}]
       ["" :home]
       [true :not-found]]])

(defn ^:export init []
  (f/init render-page {:routes routes})
  (ds/transact! (f/conn) [{:app/state :misc :started-at (js/Date.)}]))

(comment
  (f/render)
  (ds/transact! (f/conn) [{:app/state :misc :started-at (js/Date.)}])
  (nxr/dispatch (f/conn) (comment "dispatch data here") [[::f/db [{:app/state :misc :started-at (js/Date.)}]]])
  (history/listen! (partial nxr/dispatch (f/conn))) ; deactivates any old listener
  (ds/datoms @(f/conn) :eavt)
  )

(comment
  (require '[dataspex.core :as dataspex])
  (dataspex/inspect "DB" (f/conn)) ; TODO 2026-06-29 12:44:47 get working (did plugin pwn my Chrome?!)
  (dataspex/inspect-taps)
  )
