(ns pilaf.core
  (:require [korma.core :refer :all]
            [pilaf.transform :as pt]))

(defmulti entity-attr key)

(defmethod entity-attr :default [[k v]] {k [v]})

(defmethod entity-attr :has-many [[k v]] {k v})
(defmethod entity-attr :belongs-to [[k v]] {k v})

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

(defmethod combine-args :has-many [_ args] args)
(defmethod combine-args :belongs-to [_ args] args)

(defmethod combine-args :entity-fields [_ args] args `[#{~@(apply concat args)}])

(def ^:dynamic *common-mixins* nil)

(defn- intermediate-entity
  [entity-def]
  (reduce
   (fn [m [attr & args]]
     (let [k (keyword (name attr))
           args (vec args)]
       (if (#{:has-many :belongs-to} k)
         (update-in m [k] (comp vec conj) args)
         (assoc m k args))))
   {} entity-def))

(defn- multiple-forms [k forms]
  (map (fn [form] (cons k form)) forms))

(defmacro defentity+
  "Extended version of korma's defentity macro.
  Adds recognition for the following properties:
  - field-transforms - declares field transformations for both preparing and transforming field values,
                       without having to declare the transformation in both transform and prepare functions.
  - mixins? - if mixins? is provided (vector of defmixin's), then each mixin will be folded into the final korma
            entity map that defentity provides"
  [entity-name mixins? & entity-def]
  (let [[mixins entity-def] (if (vector? mixins?)
                              [(map #(if (symbol? %)
                                       (var-get (ns-resolve *ns* %)) %)
                                    (concat mixins? *common-mixins*))
                               entity-def]
                              [[] (cons mixins? entity-def)])
        entity-def-map (map entity-attr (intermediate-entity entity-def))
        mixins-def-map (apply concat (map #(map entity-attr %) mixins))
        comped-attrs (reduce
                      (fn [m [k vs]]
                        (assoc m k (combine-args k vs)))
                      {} (apply merge-with concat (concat mixins-def-map entity-def-map)))]
    `(defentity
       ~entity-name
       ~@(mapcat (fn [x]
                (let [k-sym (-> x key name symbol)]
                  (if (#{'has-many 'belongs-to} k-sym)
                    (multiple-forms k-sym (val x))
                    [(apply list
                             k-sym
                             (first (val x)))])))
              comped-attrs))))

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
       (quote ~mixin-map))))


(defmacro with-mixins
  "Apply 1 or more mixins to a set of entities"
  [mixins & entities]
  `(do
     ~@(map (fn [ent]
              (if (= "defentity+" (name (first ent)))
                (let [[mixins start] (if (vector? (nth ent 2))
                                       [(vec (concat (nth ent 2) mixins)) 3]
                                       [mixins 2])]
                  (concat (take 2 ent)
                          [mixins]
                          (drop start ent)))
                ent))
            entities)))


