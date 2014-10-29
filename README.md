# pilaf

A collection of extensions and utilities for [Sql Korma](http://sqlkorma.com/) that we found we were rewriting for every project.

## Install

Very much an early version at the moment. Use at own risk!

### Clojars and Leiningen

[![Clojars Project](http://clojars.org/pilaf/latest-version.svg)](http://clojars.org/pilaf)

## Usage

### defentity+

Adds the following conveniences to defentity.

#### field-transforms
Apply field-level, two-way transformations for individual fields in a table.  This can already be done using (transform) and (prepare), however the use of field-transforms factors away a bit of the boilerplate thats required.


```clojure
;; With our user entity, we want to convert the 'activation-status' field value from
;; the number value it's stored as, to a more descriptive and readable keyword value

(use 'pilaf.core)
(use 'pilaf.transform)
(use 'korma.core)

(defentity+ user
   (prepare (fn [u] (assoc u :added true)))
   (field-transforms [(status-field :activation-status {:active 1 :inactive 2})]))

;; with record: {name "", activation_status 1, user_role "BIG_SHOT"}
(select user (where {:id 1})) ;; => ({:name "Joe Blogg" :activation-status :active :added true :user-role "BIG_SHOT"})

;; The field-transforms form takes a seq of maps that match the following:
(def keyword-field {:field :user-role :to-db name :from-db keyword})

;; The [to-db,from-db] values are functions. to-db takes a value and coerces it into the required jdbc type.
;; from-db takes the jdbc type and coerces it into the required clojure type.

(defentity+ user
   (field-transforms [(status-field :activation-status {:active 1 :inactive 2})
                       keyword-field]))
(select user (where {:id 1}))
;; => ({:name "Joe Blogg" :activation-status :active :user-role :BIG_SHOT})
(insert user (values {:id 2 :name "Jimmy Jims" :activation-status :inactive :user-role :NOBODY}))
;; => {name "Jimmy Jims" activation_status 2 user_role "NOBODY"}

;; 'keyword-field' and 'date-field' functions are provided in pilaf.transform ns for conveniently defining the required maps
;; for transformation

(defentity+ user-log
   (field-transforms [(date-field :record-date) (keyword-field :type)]))

(select user-log (where {:id 1}))
;; => ({:record-date #inst 2014-01-01, :type :some-value})

```

### Mixins
Re-use the same properties across multiple entities.


```clojure
;; A use-case for mixins is a user tracking/audit column.

(use 'pilaf.core)
(use 'korma.core)
(use 'clj-time.core)

(defmixin user-auditable
  ;; When saving this record, add an updated-by value if user id is bound to the thread
  (prepare (fn [ent]
              (if *some-bound-user-id*
                (assoc ent :updated-by *some-bound-user-id*)
                ent))))


(defentity+ user
  [user-auditable]
  (field-transforms [(status-field :activation-status {:active 1 :inactive 2})]))

(defentity+ transaction
  [user-auditable]
  (prepare (fn [tr] (assoc tr :tran-date (now))))) ;; any prepare/transform functions will be comp'd,
                                                   ;;with the entity's func being the last func in the composition

```




## Authors

[Yair Iny](https://github.com/icm-yairiny) - wrote the original version of these utilities

[Brendan Bates](https://github.com/bbbates)

## License

Copyright © 2014 [ICM Consulting](http://www.icm-consulting.com.au)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
