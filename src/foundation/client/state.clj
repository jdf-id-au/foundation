(ns foundation.client.state)

(defn do!
  "Dummy for clojure. See cljs for realz."
  [name event-fn coeffects args]
  (println "Called dummy do! with" name event-fn coeffects args))

(defmacro defevent
  "Define two fns: <name> for actual use and <name>-impl for testing.
   See documentation for foundation.client.state/do!"
  [name event-fn & coeffects]
  (let [cofx# (vec coeffects)
        doc# (str "Event called `" name "` with coeffects " cofx#)
        impl-name# (str name "-impl")
        impl-doc# (str "Implementation of event called `" name "` for use with coeffects " cofx#)]
    `(do
       ; FIXME no arity warnings or docstrings in clojurescript
       (def ^{:doc ~impl-doc#} ~(symbol impl-name#) ~event-fn)
       (defn ~(symbol name) ~doc#
         [& ~'args] (~'foundation.client.state/do! ~(str name) ~event-fn '~cofx# (vec ~'args))))))