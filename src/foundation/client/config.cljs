(ns foundation.client.config
  (:require-macros [foundation.client.config]))

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
  (let [{:keys [dev] :as from-disk} (foundation.client.config/config)]
    (merge (dissoc from-disk :dev)
           (if debug? dev)
           (js->clj js/config :keywordize-keys true))))