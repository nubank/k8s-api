(ns kubernetes-api.misc)

(defn find-first [pred coll]
  (first (filter pred coll)))

(defn indexes-of [pred coll]
  (keep-indexed #(when (pred %2) %1) coll))

(defn first-index-of [pred coll]
  (first (indexes-of pred coll)))

(defn map-vals [f coll]
  (into {} (map (fn [[k v]] [k (f v)]) coll)))

(defn map-keys [f coll]
  (into {} (map (fn [[k v]] [(f k) v]) coll)))

(defn assoc-some
  "Assoc[iate] if the value is not nil.
  Examples:
    (assoc-some {:a 1} :b false) => {:a 1 :b false}
    (assoc-some {:a 1} :b nil) => {:a 1}"
  ([m k v]
   (if (nil? v) m (assoc m k v)))
  ([m k v & kvs]
   (let [ret (assoc-some m k v)]
     (if kvs
       (if (next kvs)
         (recur ret (first kvs) (second kvs) (nnext kvs))
         (throw (IllegalArgumentException.
                 "assoc-some expects even number of arguments after map/vector, found odd number")))
       ret))))
