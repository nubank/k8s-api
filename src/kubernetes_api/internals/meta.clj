(ns kubernetes-api.internals.meta)

(defn group-version
  "Receives a apiVersion and returns a map with group and version
 
   Example
   (group-version \"apps/v1\") => {:group \"apps\" :version \"v1\"}
   (group-version :apps/v1) => {:group \"apps\" :version \"v1\"}
   (group-version :core/v1) => {:group \"\" :version \"v1\"}
   (group-version :v1) => {:group \"\" :version \"v1\"}
   "
  [api]
  (letfn [(group [s] (if (= s "core") "" s))]
    (cond
      (nil? api) nil
      (string? api) (group-version (keyword api))
      (and (keyword? api) (seq (namespace api))) {:group (group (namespace api))
                                                  :version (name api)}
      (keyword? api) {:group (group (name api))})))
