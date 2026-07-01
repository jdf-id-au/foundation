(ns foundation.client.api)

;; NB 2026-07-01 17:28:59 cider cljs :cljs/quit doesn't restore clj repl until eval something directly in it...?

(defmacro setup!
  "def five symbols in calling ns" ; not aliasing `require`s to prevent pollution
  [gen-hiccup opts & body]
  `(let [opts# ~opts]
     (require '[replicant.dom])
     (require '[nexus.registry])
     (require '[datascript.core])
     (require '[foundation.client.history])
     (require '[foundation.client.default])
     
     (defonce ~'conn
       (doto (datascript.core/create-conn (merge foundation.client.default/schema (:schema opts#)))
         (datascript.core/transact! (into foundation.client.default/tx-data (:tx-data opts#)))))
     (defonce ~'element (js/document.getElementById (:element opts# "app")))
     (def ~'dispatch (partial nexus.registry/dispatch ~'conn))
     (defn ^:dev/after-load ~'render [] (replicant.dom/render ~'element (~gen-hiccup @~'conn)))
     (defn ^:export ~'init []
       (replicant.dom/set-dispatch! ~'dispatch)
       (foundation.client.history/setup! (:routes opts#))
       (foundation.client.history/listen! ~'dispatch)
       (add-watch ~'conn ::render
         (fn [~'_ ~'_ ~'_ ~'_] (replicant.dom/render ~'element (~gen-hiccup @~'conn))))
       ~@body)))

(comment
  (setup! gen-hic {:element "hmm"} (ds/do-something @conn)) ; use cider-macroexpand-1 (C-c RET) to inspect
  )
