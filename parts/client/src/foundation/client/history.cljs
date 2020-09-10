(ns foundation.client.history
  (:require [goog.events]
            [bidi.bidi :as bidi]
            [foundation.client.events :refer [navigate]]
            [foundation.client.logging :as log])
  (:import (goog.history Html5History EventType)))

; Routing support

(defonce -routes (atom []))

(defn setup! [routes] (reset! -routes routes))

(defn routed-views [routes]
  (set (filter keyword? (tree-seq map? vals (get-in routes [1 0 1])))))

(defn path-for
  "Return navigation token for given route."
  [handler route-params]
  ; unwrap route-params map into k v arguments for bidi
  (log/debug handler route-params)
  (apply bidi/path-for @-routes handler
         (mapcat (juxt first (comp str second)) route-params)))

; History

(defonce history (atom nil))

(defn navigate!
  "Update browser address bar url #token. `route-params` values are stringified in `path-for`."
  ([token] (.setToken @history token))
  ([handler route-params] (navigate! (path-for handler route-params))))

(defn navigated
  "Callback for EventType.NAVIGATE ."
  [event]
  (let [token (.-token event)
        {:keys [handler route-params]} (bidi/match-route @-routes token)
        path-check (path-for handler route-params)]
    (if (= token path-check)
      (do (log/debug "Navigated" token handler route-params)
          (navigate handler route-params))
      (do (log/debug "Corrected token" token "to" path-check)
          (.replaceToken @history path-check))))) ; redirect to canonical token

(defn listen!
  "Listen for browser address bar url #token change."
  []
  (doto (reset! history (Html5History.))
    ; TODO refuse if (false? (.isSupported Html5History))
    ; I actually prefer token after # because it doesn't reload on manual entry.
    (.setUseFragment true)
    ; https://developers.google.com/closure/library/docs/events_tutorial
    (.listen EventType.NAVIGATE #(navigated %)) ; fn wrap allows hot reload
    (.setEnabled true)))

(defn unlisten!
  "Clear history listener(s). Dev convenience only."
  []
  (if @history
    (let [capturing? #(count (.getListeners @history EventType.NAVIGATE %))]
      (log/info "Removing" (capturing? true) "capturing and"
                           (capturing? false) "non-capturing listeners.")
      (.removeAllListeners @history))
    (log/warn "No history object!")))