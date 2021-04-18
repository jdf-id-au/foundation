(ns shared
  (:require [foundation.message :refer [message ->server ->client]]
            #?@ (:clj  [[clojure.spec.alpha :as s]]
                 :cljs [[cljs.spec.alpha :as s]])))

(message :ping ->server :code keyword? :word string?)
(message :pong ->client :code keyword? :word string?)