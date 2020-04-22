(ns foundation.client.api)

(defmacro defevent
  "Define two fns: <name> for actual use and <name>-impl for testing.
   See documentation for foundation.client.state/do!"
  [name event-fn & coeffects]
  (let [cofx# (vec coeffects)
        doc# (str "Event called `" name "` with coeffects " cofx#)
        impl-name# (str name "-impl")
        impl-doc# (str "Implementation of event called `" name "` for use with coeffects " cofx#)]
    `(do
       ; FIXME no docstring in clojurescript (do get arity warning)
       (def ^{:doc ~impl-doc#} ~(symbol impl-name#) ~event-fn)
       ; FIXME no arity warning or docstring in clojurescript
       (defn ~(symbol name) ~doc#
         [& ~'args] (~'foundation.client.events/do! ~(str name) ~event-fn '~cofx# (vec ~'args))))))