(ns foundation.client.core
  "Facilitate automatic testing in browser.
   Ensure all relevant test namespaces are required (nested)."
  (:require [cljs.test :refer [report] :refer-macros [deftest is run-all-tests]]
            [foundation.client.favicon :as favicon]
            [test.common.core]))

; Should be ~idempotent so not mind when re-run from client.core/init
(favicon/insert!)

; https://github.com/bhauman/crashverse/blob/master/test/crashverse/test_runner.cljs
(defmethod report [:cljs.test/default :summary]
  ; Patch cljs.test report to change favicon colour.
  [m]
  (println "Ran" (:test m) "tests containing"
    (+ (:pass m) (:fail m) (:error m)) "assertions.")
  (println (:fail m) "failures," (:error m) "errors.")
  (condp #(pos? (%1 %2)) m
    :error (favicon/red!)
    :fail (favicon/orange!)
    (favicon/green!)))

; TODO use fixtures where required, otherwise just take advantage of cljs ns loading machinery
; FIXME need to require ns to be tested... how to make work in library?
(defn test! []
  (run-all-tests #"test\..*"))