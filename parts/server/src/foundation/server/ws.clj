(ns foundation.server.ws
  (:require [sok.api :as sok]
            [foundation.server.api :as fs]
            [comfort.core :as cc]
            [foundation.message :as message :refer [->transit <-transit]]
            [clojure.core.async :as async :refer [chan go go-loop thread >! <! >!! <!! alt! timeout]]
            [taoensso.timbre :as log]))

(defn send!
  "Send `msg` to connected `user`s over their registered websocket/s.
   See `f.common.message` specs."
  [out user-msg-map]
  (go (doseq [[username msg] user-msg-map
              :let [[type & _ :as validated]
                    (or (fs/validate msg) [:error :outgoing "Problem generating server reply." msg])]]
        (when (= type :error) (log/warn "Telling" username "about server error" msg))
        (when-not (>! out [username validated])
          (log/error "Dropped outgoing message to" username
            "because application out chan is closed" msg)))))
        ; TODO catch exceptions?

(defn setup!
  "Set up websocket server using sok.api/server!
   Format its in/out chans with transit.
   Hook into auth system."
  [& args]
  (let [{:keys [clients] :as server} (apply sok/server! args)
        out (chan)
        _ (go-loop []
            (if-let [[username msg] (<! out)]
              (do ; tell user on as many channels as they may be connected
                ; NB could extend this to broadcast by role too (put roles in at auth-ws etc)
                ; although risk getting out of sync with actual db roles!
                (doseq [id (reduce (fn [[ids [id {existing :username}]]]
                                     (if (= existing username) (conj ids id) ids))
                             #{} @clients)]
                  (when-not (>! (server :out) [id (->transit msg)])
                    (log/error "Dropped outgoing message to" username
                      "because server out chan is closed" msg)))
                (recur))
              (log/info "Stopped sending messages")))
        in (chan)
        _ (go-loop []
            (if-let [[id msg] (<! (server :in))]
              (let [{:keys [username]} (@clients id)]
                (when-not (>! in [username (<-transit msg)])
                  (log/error "Dropped incoming message from" username
                    "because application in chan is closed" msg))
                (recur))
              (log/warn "Tried to read from closed server in chan")))
        _ (go-loop []
            (if-let [[username msg] (<! in)]
              (if-let [conformed (fs/conform msg)]
                (do (message/receive clients out username conformed)
                    (recur))
                (send! out {username [:error :incoming "Invalid message sent to server." msg]}))
              (log/info "Stopped receiving messages")))]
    ; Hide sok.server's in/out channels with foundation's
    ; `in` is processed above so shouldn't need to access it directly
    (assoc server :in in :out out)))

(defn auth-ws [clients ws-id username]
  (swap! clients cc/update-if-present ws-id
    (fn [{existing :username :as client-meta}]
      (if (and existing (not= existing username))
        (log/error "Websocket already associated with different user!" existing "vs" username ws-id)
        (assoc client-meta :username username)))))
; TODO will we rely on ws (over tls!) integrity for authorisation? i.e. no tokens?? xss etc??

; TODO listen to clients and log (connect?)/auth/disconnect

; NB decoupling explicit "every incoming message must return outgoing message" thing
; should be good!
; will need to rework lt proj