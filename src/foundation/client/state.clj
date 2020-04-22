(ns foundation.client.state)

(defmacro defevent
  "Define two fns: <name> for actual use and <name>-impl for testing.
   See documentation for foundation.client.state/do!"
  [name event-fn & coeffects]
  `(defn ~name [& ~'args] (~'foundation.client.state/do! event-fn ~coeffects ~'args))
  `(def (str ~name "-impl") event-fn))