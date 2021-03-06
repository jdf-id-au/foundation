(ns foundation.message
  "Define messages to be sent between client and server."
  (:require [cognitect.transit :as transit]
            [comfort.gen :refer [retag]]
            [temper.transit :as tt]
            #?@(:clj  [[taoensso.timbre :as log]
                       [clojure.spec.alpha :as s]
                       [manifold.stream :as ms]]
                :cljs [[foundation.client.logging :as log]
                       [cljs.spec.alpha :as s]
                       [com.cognitect.transit.types :as ty]]))
  #?(:cljs (:require-macros [foundation.message :refer [message]]))
  #?(:clj (:import (java.io ByteArrayOutputStream ByteArrayInputStream))))

; Java streams

#?(:clj (defn format-stream
          [s freeze thaw]
          (let [out (ms/stream)]
            (ms/connect (ms/map freeze out) s)
            (ms/splice out (ms/map thaw s)))))

; Transit

; Let cljs.core/uuid? work on transit uuids.
; https://github.com/cognitect/transit-cljs/issues/18
; TODO remove when upstream fixed
#?(:cljs (extend-type ty/UUID IUUID))

(def read-handlers tt/read-handlers)
(def write-handlers tt/write-handlers)

(defn ->transit "Encode data structure to transit."
  [arg]
  #?(:clj  (let [out (ByteArrayOutputStream.)
                 writer (transit/writer out :json write-handlers)]
             (transit/write writer arg)
             (.toString out))
     :cljs (transit/write (transit/writer :json write-handlers) arg)))

(defn <-transit "Decode data structure from transit."
  [json]
  #?(:clj  (try (let [in (ByteArrayInputStream. (.getBytes json))
                      reader (transit/reader in :json read-handlers)]
                  (transit/read reader))
                (catch Exception e
                  ;(log/warn "Invalid message" json (.getMessage e))
                  :invalid-message))
     :cljs (transit/read (transit/reader :json read-handlers) json)))
; TODO catch js errors

; Wire protocol

#?(:clj (defmacro message
          "Define spec method for a given message type and multimethod using `s/cat`.
           Message must be a vector starting with type keyword, optionally followed by
           key/predicate pairs."
          ; Key-pred redundancy is interesting but won't factor out just yet.
          [type multi & catspec]
          (assert (keyword? `~type))
          `(defmethod ~multi ~type [~'_]
             ; I don't 100% understand why ~'s/cat works cross-platform, but it does!
             ; Unquoting (evaluating) a quoted symbol just gives the symbol, I think?
             (~'s/cat :type #{~type} ~@catspec))))

(defmulti ->client first)
#_(message :error ->client :code keyword? :message string? :context (s/? any?))
#_(message :ready ->client)
(s/def ::->client (s/multi-spec ->client retag))

(defmulti ->server first)
#_(message :init ->server)
(s/def ::->server (s/multi-spec ->server retag))

(defn conform
  "Conform incoming message according to (directional) spec."
  [spec msg]
  (let [v (s/conform spec msg)]
    #_(log/debug "Conforming incoming message" msg v)
    #_(def debug-incoming msg)
    (case v ::s/invalid (log/error "Invalid incoming message" msg) v)))

(defn validate
  "Validate outgoing message according to (directional) spec."
  [spec msg]
  #_(log/debug "Validating outgoing message" msg "against" spec)
  #_(def debug-outgoing msg)
  (if-let [explanation (s/explain-data spec msg)]
    (log/error "Invalid outgoing message" msg explanation)
    msg))

; Cursive doesn't get docstrings right if (defmulti receive #?(:clj...)).
#?(:clj  (defmulti receive ; TODO *** implement (ws only?)
           "Called by `f.server/ws-receive` with [ws-send clients user conformed-msg].
            See specs."
           (fn dispatch [clients user {:keys [type]}] type))
   :cljs (defmulti receive ; TODO *** implement (ws and ajax)
           "Called by the `f.client.connection/receive` defevent without coeffects.
            If a particular receive method needs coeffects, it can call another defevent itself.
            This inner defevent will return nil, and cause f.c.c/receive to do nothing further."
           :type))
