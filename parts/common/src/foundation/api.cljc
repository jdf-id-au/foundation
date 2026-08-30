(ns foundation.api
  (:require [comfort.core :as cc]))

(defn endpoint
  "Return endpoint uri given config."
  [{:keys [scheme tls] :as config} {:keys [path params fragment] :as loc}]
  (cc/url (cond-> config
            (not scheme) (assoc :scheme "http")
            tls (update :scheme #(str % \s))
            loc (merge loc))))

(defn client
  "Return client uri given config."
  [{:keys [origin-port client-root] :as config}
   {:keys [path params fragment] :as loc}]
  (endpoint
    (cond-> config
      origin-port (assoc :port origin-port))
    (cond-> loc
      client-root (update :path #(str client-root \/ %)))))
