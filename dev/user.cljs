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
            [foundation.message :as fm]
            [oops.core :refer [oget oset!]]))

;; NB 2026-06-29 10:42:31 my way was nice but replicant/nexus probably better
;; TODO 2026-06-29 11:52:26 hook up history, think about architecture, clean up rest of foundation.client

(nxr/register-system->state! deref)
(nxr/register-effect! :db/transact
  (fn [_ conn tx-data]
    (ds/transact! conn tx-data)))
(nxr/register-effect! ::post
  (fn [{:keys [dispatch]} system [endpoint msg] {:keys [on-success on-failure]}]
    (-> (js/fetch (if (vector? endpoint) (apply config/api endpoint) (config/api endpoint))
          #js {:method "POST" :body (fm/encode msg) :keepalive true
               :headers #js {:content-type fm/transit-mime-type :accept fm/transit-mime-type}})
      ;; TODO 2026-06-29 20:48:26 verify cookies come along for the ride
      (.then
        (fn [response]
          (case (oget response "status")
            200 (-> (.text response)
                  (.then
                    (fn [v] (-> v fm/decode fm/receive))
                    (fn [e] (log/warn "Failed to read" response))))
            401 (log/warn "Unauthenticated" response)
            403 (log/warn "Unauthorised" response)
            (log/warn "Unsupported status" response))
          (when on-success (dispatch on-success {:response response}))) ; "dispatch data will be merged into the original dispatch data"
        (fn [error] (if on-failure (dispatch on-failure {:error error})
                        (log/warn "Unhandled fetch error" error)))))))
;; TODO 2026-06-29 14:25:40 think about dispatch beyond just receive

;; nxr/register-interceptor! for render lock etc https://github.com/cjohansen/nexus/blob/main/Readme.md#rendering
(nxr/register-placeholder! ::now (fn [] (js/Date.)))

(defonce conn (ds/create-conn {}))
(defonce el (js/document.getElementById "app"))

(comment
  (require '[dataspex.core :as dataspex])
  (dataspex/inspect "DB" conn) ; TODO 2026-06-29 12:44:47 get working (did plugin pwn my Chrome?!)
  (dataspex/inspect-taps)
  )

(defn render-page [db]
  (let [app (ds/entity db :system/app)]
    [:div
     [:h1 "jdf/foundation"]
     [:h2 "v" (:version config/config)]
     [:div "debug mode: " (if config/debug? "on" "off")]
     [:div "config from html: " (:from_html config/config)]
     [:p "started at" (:app/started-at app)]
     [:button {:on {:click [[:db/transact [{:db/ident :system/app :app/started-at [::now]}]]]}}
      "fib about time"]
     [:button {:on {:click [[::post ["hello" [:ping :hello "message from button"]]]]}} ; dev/common.cljc message :ping
      "ping server"]]))

(defn main [conn]
  (add-watch conn ::render
    (fn [_ _ _ _]
      (r/render el (render-page @conn))))
  (r/set-dispatch!
    (fn [dispatch-data actions]
      (nxr/dispatch conn dispatch-data actions)))
  (ds/transact! conn [{:db/ident :system/app
                       :app/started-at (js/Date.)}]))

(comment
  (ds/transact! conn [{:db/ident :system/app
                       :app/started-at (js/Date.)}])
  )

(defmethod fm/receive :pong [msg]
  (log/info "Received" msg))

(defn ^:dev/after-load start [] (r/render el (render-page @conn)))

(defn ^:export init []
  (main conn))

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
