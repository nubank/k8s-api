(ns kubernetes-api.internals.client
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as string]
            [kubernetes-api.misc :as misc]))

(defn pascal-case-routes [k8s]
  (update k8s :handlers
          (fn [handlers]
            (mapv #(update % :route-name csk/->PascalCase) handlers))))

(defn ^:private swagger-definition-for-route [k8s route-name]
  (->> (:handlers k8s)
       (misc/find-first #(= route-name (:route-name %)))
       :swagger-definition))

(defn handler-kind [handler]
  (-> handler :swagger-definition :x-kubernetes-group-version-kind :kind keyword))

(defn handler-action [handler]
  (-> handler :swagger-definition :x-kubernetes-action keyword))

(defn ^:private all-namespaces-route? [route-name]
  (string/ends-with? (name route-name) "ForAllNamespaces"))

(defn find-action [k8s {:keys [kind action all-namespaces?] :as _search-params}]
  (->> (:handlers k8s)
       (filter (fn [handler]
                 (and (or (= (keyword kind) (handler-kind handler)) (nil? kind))
                      (or (= (keyword action) (handler-action handler)) (nil? action))
                      (= (boolean all-namespaces?) (all-namespaces-route? (:route-name handler))))))
       (map :route-name)))

(defn version-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :version))

(defn group-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :group))

(defn kind-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :kind
       keyword))

(defn core-versions [k8s]
  (mapv
   #(hash-map :name ""
              :versions [{:groupVersion % :version %}]
              :preferredVersion {:groupVersion % :version %})
   (:versions (:kubernetes-api.core/core-api-versions k8s))))

(defn all-versions [k8s]
  (concat (:groups (::api-group-list k8s))
          (core-versions k8s)))

(defn choose-preffered-version [k8s route-names]
  (misc/find-first
   (fn [route]
     (some #(and (= (:name %) (group-of k8s route))
                 (= (:version (:preferredVersion %)) (version-of k8s route)))
           (all-versions k8s)))
   route-names))

(defn find-preferred-action [k8s search-params]
  (->> (find-action k8s search-params)
       (filter (fn [x] (not (string/ends-with? (name x) "Status"))))
       ((partial choose-preffered-version k8s))))
