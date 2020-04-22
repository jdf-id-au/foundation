(ns foundation.client.state)

(defn do!
  "Dummy for clojure. See cljs for realz."
  [event-fn coeffects args]
  (println "Called dummy do! with" event-fn coeffects args))

(defmacro defevent
  "Define two fns: <name> for actual use and <name>-impl for testing.
   See documentation for foundation.client.state/do!"
  [name event-fn & coeffects]
  (let [doc# (str "Event called `" name "` with coeffects " (vec coeffects))
        impl-name# (str name "-impl")
        impl-doc# (str "Implementation of event called `" name "` with coeffects " (vec coeffects))]
    `(do
       (defn ~(symbol impl-name#) ~impl-doc#
         [& ~'args] (apply ~event-fn ~@coeffects ~'args))
       (defn ~(symbol name) ~doc#
         [& ~'args] (~'foundation.client.state/do! ~event-fn '~coeffects ~'args)))))
