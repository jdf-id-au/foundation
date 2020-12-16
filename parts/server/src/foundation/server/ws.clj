(ns foundation.server.ws
  "Fully asynchronous transit-over-websockets with per-channel authentication"
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

(defn auth [clients ws-id username]
  (swap! clients cc/update-if-present ws-id
    (fn [{existing :username :keys [addr] :as client-meta}]
      (if (and existing (not= existing username))
        (log/error "Websocket already associated with different user!" existing "vs" username addr)
        (do (assoc client-meta :username username)
            (log/info "authenticated" username "at" addr))))))
; TODO rely on ws (over tls!) integrity for authentication? i.e. no tokens? can wss be hijacked on client side?

#_ (defn auth [unauth clients]
     (go-loop []
       (if-let [[ws-id msg] (<! unauth)] ; TODO validate
         (do (if-let [{:keys [user password]} (fs/conform msg)]
               (comment "application does auth here")
               (log/warn "Unrecognised message on unauth chan")))
         (log/warn "Tried to read from closed application auth chan"))))

(defn setup
  "Set up websocket server using sok.api/server!
   Format its in/out chans with transit.
   Hook into auth system.
   Application needs to process unauth channel and call `auth`"
  [& args]
  (let [{:keys [clients] :as server} (apply sok/server! args)
        out (chan)
        _ (go-loop []
            (if-let [[username msg] (<! out)] ; TODO validate
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
        unauth (chan)
        _ (go-loop []
            (if-let [[ws-id msg] (<! (server :in))] ; TODO validate
              (do (if-let [{:keys [username addr] :as client-meta} (@clients ws-id)]
                    (case msg
                      true (log/info (or username "unknown") "connected at" addr "on" ws-id)
                      false (log/info (or username "unknown") "disconnected at" addr)
                      (if username
                        (when-not (>! in [username (<-transit msg)])
                          (log/error "Dropped incoming message from" username
                            "because application in chan is closed" msg))
                        (when-not (>! unauth [ws-id (<-transit msg)])
                          (log/error "Dropped incoming unauth message because unauth chan is closed"
                            msg))))
                    (log/error "Websocket id not found in clients registry" @clients ws-id))
                  (recur))
              (log/warn "Tried to read from closed server in chan")))
        _ (go-loop []
            (if-let [[username msg] (<! in)] ; TODO validate
              (do (if-let [conformed (fs/conform msg)]
                    (message/receive clients out username conformed)
                    (send out {username [:error :incoming "Invalid message sent to server." msg]}))
                  (recur))
              (log/info "Stopped receiving messages")))]
    (-> server (dissoc :in) (assoc :out out :unauth unauth))))