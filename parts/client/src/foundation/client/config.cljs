(ns foundation.client.config
  (:require-macros [foundation.config :refer [from-disk]]))

(def default
  "Default config here, not foundation.client.default because of circular dependency."
  {:timeout 5000
   :log-level :info})

(def debug? ^boolean goog.DEBUG)
(def config
  "Bring config from build-client.edn across at build time via macro.
   In debug mode (e.g. non-release shadow-cljs build), the :dev map is merged into its parent.
   Config is overwritten by any js config which is specified in index.html, e.g.:

   <script>
     'use strict';
     const config = {
       port: 443
     };
     Object.freeze(config);
   </script>
   "
  (let [{:keys [dev] :as from-disk} (from-disk)]
    (merge default
           (dissoc from-disk :dev)
           (when debug? dev)
           (js->clj js/config :keywordize-keys true))))
           ; TODO could re-spec here

(defn usp ; TODO 2026-06-29 21:12:54 could move if needed elsewhere, e.g. own ns (avoid circular dep)
  "Clojure map -> URLSearchParams; would lose ordering but I hope you're not relying on that!"
  [params]
  (reduce (fn [usp [k v]] (.append usp (name k) v) usp)
    (js/URLSearchParams.)
    params))

(defn api
  ([path] (api "http" path))
  ([scheme path] (api scheme path nil))
  ([scheme path params]
   (let [{:keys [tls host port root]
          :or {tls true root "/"}} config]
     (str scheme (when tls "s") "://"
          host (when port (str ":" port))
          root path (some->> params usp .toString (str \?))))))
