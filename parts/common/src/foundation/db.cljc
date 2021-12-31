(ns foundation.db
  "Datascript schema assistance")

(def primary-key {:db/unique :db.unique/identity})
(def foreign-key {:db/valueType :db.type/ref})
(def cascade {:db/isComponent true})
(def single {:db/cardinality :db.cardinality/one})
(def multiple {:db/cardinality :db.cardinality/many})
(def to-one (merge single foreign-key))
;(def to-many (merge multiple foreign-key)) ; TODO use reverse lookup instead?

(def indexed {:db/index true})
