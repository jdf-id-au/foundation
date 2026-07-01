(ns foundation.client.history
  "Eerily similar to bidi.router..."
  (:require [bidi.bidi :as bidi]
            [foundation.client.logging :as log]))

;; ───────────────────────────────────────────────────────────── Routing support

(defonce -routes (atom []))

(defn setup! [routes] (reset! -routes routes))

(defn routed-views [routes]
  (set (filter keyword? (tree-seq map? vals (get-in routes [1 0 1])))))

(defn path-for
  "Return navigation token for given route."
  [handler route-params]
  ; unwrap route-params map into k v arguments for bidi
  (apply bidi/path-for @-routes handler
    (mapcat (juxt first (comp str second)) route-params)))

;; ───────────────────────────────────────────────────────────────────── History

(defn -navigate! [hash]
  (.assign js/window.location (str \# hash)))

(defn navigate!
  "Update browser address bar url #hash. `route-params` values are stringified in `path-for`.
  Suitable for registration with nxr/register-effect!"
  [{:keys [dispatch]} system [handler route-params]]
  (-navigate! (path-for handler route-params)))

(defn canonicalise [hash dispatch]
  (let [hash (subs hash 1) ; drop initial "#" from js apis (restored in -navigate!)
        {:keys [handler route-params]} (bidi/match-route @-routes hash)
        path-check (path-for handler route-params)]
    (if (= hash path-check)
      (do (log/debug "Navigated" hash handler route-params)
          (dispatch nil [[:db [[:db/add [:app/state :ui] :view handler]
                               (if route-params
                                 [:db/add [:app/state :ui] :route-params route-params]
                                 [:db.fn/retractAttribute [:app/state :ui] :route-params])]]]))
      (do (log/warn "Corrected hash" hash "to" path-check)
          (-navigate! path-check)))))

(defn navigated [dispatch]
  (fn [^js/NavigateEvent event]
    (when (.-hashChange event)
      (canonicalise (-> event .-destination .-url js/URL. .-hash)
        dispatch))))

(defonce listener (atom nil))

(defn listen! [dispatch]
  (when @listener
    (log/debug "Removing previous navigate listener")
    (.removeEventListener js/window.navigation "navigate" @listener))
  (reset! listener (navigated dispatch))
  (.addEventListener js/window.navigation "navigate" @listener)
  (canonicalise (.-hash js/window.location) dispatch)) ; handle first-visit hash

(defn back! [_ _ _] (.back js/window.history))
(defn restart! [_ _ _] (.assign js/window.location "#"))
