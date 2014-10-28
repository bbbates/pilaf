(ns
  ^{:doc "Perform transformations on incoming and outgoing records."}
  pilaf.transform
  (:require [clj-time.coerce :refer [to-date from-date to-sql-time]]))

(defn- to-status
  "Create a fn to convert from a status code (db field) to a status keyword using the given map"
  [status-map]
  (fn [field-val]
    (if-let [ret (-> (filter #(= (val %) field-val) status-map) first first)]
      ret
      (throw (IllegalArgumentException.
               (format "Unable to find status for value %d in map %s" field-val status-map))))))

(defn- from-status
  "Map from a status keyword to the associated value to be stored in the db"
  [status-map]
  (fn [kw]
    (let [ret (status-map kw)]
      (if ret ret (throw (IllegalArgumentException.
                          (format "Unable to find value for %s in %s" kw status-map)))))))


(defn status-field
  "The field values are values in a particular map.
  When persisting values (insert, update), the values of the fields should be a member of the keys set of the map.
  These will get transformed to the corresponding values, or throw an IllegalArgumentException otherwise."
  [field status-map]
  {:field field :from-db (to-status status-map) :to-db (from-status status-map)})

(defn to-sql-timestamp
  [clj-time-date]
  (to-sql-time clj-time-date))

(defn date-field
  "The field is a date. Persists java.sql.Date objects, transforms to joda time (clj-time) instances when selecting."
	[field]
	{:field field :from-db from-date :to-db to-sql-timestamp})

(defn keyword-field
  "The field values should be converted to a keyword when selecting, to a string when persisting."
	[field]
	{:field field :from-db keyword :to-db name})

(defn- make-transform
  "makes a transformation, takes a rules seq where each rule is a map with :field :to-db and :from-db, and the direction to use for the rules, to-db? true means that the to-db will be used, otherwise from-db will be used"
  [rules to-db?]
  (fn [rec]
    (reduce (fn [m rule]
              (let [{:keys [field to-db from-db]} rule
                    f (if to-db? to-db from-db)]
                (if (field m)
                  (assoc m field (f (field m)))
                  m))) rec rules)))

(defn prepare
  [rules]
  (make-transform rules true))

(defn transform
  [rules]
  (make-transform rules false))
