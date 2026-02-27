(ns kubernetes-api.interceptors.encoders
  (:require martian.encoders
            martian.interceptors))

(defn patch-encoders [json]
  {"application/merge-patch+json" json
   "application/strategic-merge-patch+json" json
   "application/apply-patch+yaml" json
   "application/json-patch+json" json})

(defn default-encoders []
  (let [encoders (martian.encoders/default-encoders)]
    (merge encoders (patch-encoders (get encoders "application/json")))))


(defn new []
  (martian.interceptors/encode-request (default-encoders)))
