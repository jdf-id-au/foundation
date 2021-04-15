(ns foundation.server.auth
  (:require [comfort.core :as cc]
            [taoensso.timbre :as log]))

(defn authenticate [clients channel username]
  (swap! clients cc/update-if-present channel
    (fn [{existing :username :keys [addr] :as client-meta}]
      (if (and existing (not= existing username))
        (log/error "Channel already associated with different user!" existing "vs" username addr)
        (do (assoc client-meta :username username)
            (log/info "authenticated" username "at" addr))))))

; TODO token/header incl for ws

(defn deauthenticate [clients channel]
  (swap! clients cc/update-if-present channel dissoc :username))