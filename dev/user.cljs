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

(defonce conn (ds/create-conn default/schema)) ; known as "system" in nexus: "a mutable application object"
(nxr/register-system->state! deref)
(nxr/register-effect! :db #(ds/transact! %2 %3))
(nxr/register-effect! ::post connection/post!) ; TODO 2026-06-29 14:25:40 think about dispatch beyond just receive
(nxr/register-effect! ::navigate history/navigate!)
;; nxr/register-interceptor! for render lock etc https://github.com/cjohansen/nexus/blob/main/Readme.md#rendering
(nxr/register-placeholder! ::now (fn [] (js/Date.)))

(defn render-page [db]
  (let [misc (ds/entity db [:app/state :misc])
        ui (ds/entity db [:app/state :ui])]
    [:div
     [:h1 "jdf/foundation"]
     [:h2 "v" (:version config/config)]
     [:div "debug mode: " (if config/debug? "on" "off")]
     [:div "config from html: " (:from_html config/config)]
     [:p "started at: " (:started-at misc)]
     [:p "view: " (:view ui) " route-params: " (:route-params ui)]
     [:button {:on {:click [[::navigate [:home nil]]]}} "home"]
     [:button {:on {:click [[::navigate [:example nil]]]}} "example"]
     [:button {:on {:click [[:db [{:app/state :misc :started-at [::now]}]]]}}
      "fib about time"]
     [:button {:on {:click [[::post ["hello" [:ping :hello "message from button"]]]]}} ; dev/common.cljc message :ping
      "ping server"]]))

(defmethod message/receive :pong [msg] (log/info "Received" msg))

(def routes ; see foundation.client.default/routes
  ["" [["/" {"" :home
             "example" :example
             "not-found" :not-found}]
       ["" :home]
       [true :not-found]]])

(defonce el (js/document.getElementById "app"))
(defn ^:dev/after-load render [] (r/render el (render-page @conn)))
(defn ^:export init []
  (let [dispatch (partial nxr/dispatch conn)]
    (r/set-dispatch! dispatch)
    (history/setup! routes)
    (history/listen! dispatch))
  (add-watch conn ::render (fn [_ _ _ _] (render)))
  (ds/transact! conn [{:app/state :misc :started-at (js/Date.)}]))

(comment
  (ds/transact! conn [{:app/state :misc :started-at (js/Date.)}])
  (nxr/dispatch conn (comment "dispatch data here") [[:db [{:app/state :misc :started-at (js/Date.)}]]])
  (history/listen! (partial nxr/dispatch conn)) ; deactivates any old listener
  @conn
  (type (ds/entity @conn [:app/state :misc]))
  )

(comment
  (require '[dataspex.core :as dataspex])
  (dataspex/inspect "DB" conn) ; TODO 2026-06-29 12:44:47 get working (did plugin pwn my Chrome?!)
  (dataspex/inspect-taps)
  )

(comment ; ╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸╸ deprecated
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
              :effects {:clicked println}})))
