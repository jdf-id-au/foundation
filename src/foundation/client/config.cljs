(ns foundation.client.config
  (:require-macros [foundation.client.config :refer [from-disk]]))

(def default
  "Default config here, not foundation.client.default because of circular dependency."
  {:timeout 5000
   :log-level :warn})

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
           (if debug? dev)
           (js->clj js/config :keywordize-keys true))))

(defn api
  ([path] (api "http" path))
  ([scheme path]
   (let [{:keys [tls host port root]
          :or {tls true root "/"}} config]
     (str scheme (if tls "s") "://"
          host (if port (str ":" port))
          root path))))