(ns pilaf.core
  (:require [korma.core :refer :all]
            [pilaf.transform :as pt]))

(defmulti entity-attr key)

(defmethod entity-attr :default [[k v]] {k [v]})

(defmethod entity-attr :prepare [[k v]] {k v})

(defmethod entity-attr :transform [[k v]] {k v})

(defmethod entity-attr :field-transforms [[_ transforms]]
  {:transform [`(pt/transform ~@transforms)]
   :prepare [`(pt/prepare ~@transforms)]})

(defmethod entity-attr :entity-fields [[attr & fields]]
  {:entity-fields [(apply vec fields)]})


(defmulti combine-args (fn [k args] k))

(defmethod combine-args :default [_ args] [(last args)]) ;; take the last arg - it will override all args before it

(defmethod combine-args :transform [_ args] (if (seq (rest args)) `[[(apply comp [~@(reverse args)])]] [args]))
(defmethod combine-args :prepare [_ args] (if (seq (rest args)) `[[(apply comp [~@(reverse args)])]] [args]))

(defmethod combine-args :entity-fields [_ args] args `[#{~@(apply concat args)}])


(defn- intermediate-entity
  [entity-def]
  (reduce
   (fn [m [attr & args]]
     (assoc m (keyword (name attr)) (vec args)))
   {} entity-def))


(defmacro defentity+
  "Extended version of korma's defentity macro.
  Adds recognition for the following properties:
  - field-transforms - declares field transformations for both preparing and transforming field values,
                       without having to declare the transformation in both transform and prepare functions.
  - mixins? - if mixins? is provided (vector of defmixin's), then each mixin will be folded into the final korma
            entity map that defentity provides"
  [entity-name mixins? & entity-def]
  (let [[mixins entity-def] (if (vector? mixins?)
                              [(map #(if (symbol? %) (var-get (ns-resolve *ns* %)) %) mixins?) entity-def]
                              [[] (cons mixins? entity-def)])
        entity-def-map (map entity-attr (intermediate-entity entity-def))
        mixins-def-map (apply merge-with concat (map #(map entity-attr %) mixins))
        comped-attrs (reduce
                      (fn [m [k vs]]
                        (assoc m k (combine-args k vs)))
                      {} (apply merge-with concat mixins-def-map entity-def-map))]
    `(defentity
      ~entity-name
      ~@(map #(apply list
                     (-> % key name symbol)
                     (first (val %)))
             comped-attrs))))

(defentity+ user
  [test-mixin]
  (prepare (fn [a] a))
  (field-transforms [(pt/status-field :foo {:a 1})])
  (table :ausdat_user)
  (entity-fields :id :foo)
  (belongs-to :ausdat_user {:fk :blah}))


(defn- quote-vals
  [m-to-quote]
  (reduce (fn [m [k v]] (assoc m k (quote v))) {} m-to-quote))

(defmacro defmixin
  "Creates a defentity+ mixin, which provides re-usable properties across korma entities.
  E.g. add a dynamic-bound value to a updated-by field when saving
  ```
  (defmixin user-mixin
  (prepare (fn [m] (if *user-id* (assoc m :updated-by *user-id*) m))))
  ```
  "
  [mixin-name & mixin-def]
  (let [mixin-map (intermediate-entity mixin-def)]
    `(def ^{:type ::mixin}
       ~mixin-name
       ~mixin-map)))

(defmixin test-mixin
  (prepare (fn [a] a))
  (entity-fields :updated-by))

(defmixin another-mixin
  (prepare (fn [c] c))
  (field-transforms [(pt/status-field :blah {:a 1})])
  )

