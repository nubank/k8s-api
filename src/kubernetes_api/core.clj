(ns kubernetes-api.core
  (:require [martian.core :as martian]
            [martian.httpkit :as martian-httpkit]
            martian.swagger
            [kubernetes-api.interceptors.auth :as interceptors.auth]
            [kubernetes-api.interceptors.raise :as interceptors.raise]
            [kubernetes-api.swagger :as swagger]
            [camel-snake-kebab.core :as csk]
            clojure.data
            clojure.set
            [kubernetes-api.extensions.custom-resource-definition :as crd]
            [kubernetes-api.misc :as misc]
            [clojure.string :as string]
            [schema.core :as s]
            [clojure.walk :as walk])
  (:import (java.util Base64)))

(defn ^:private pascal-case-routes [k8s]
  (update k8s :handlers
          (fn [handlers]
            (mapv #(update % :route-name csk/->PascalCase) handlers))))

(defn client
  "Creates a Kubernetes Client compliant with martian api and its helpers

  host - a string url to the kubernetes cluster

  Options:

  [Authentication-related]
  :basic-auth - a map with plain text username/password
  :token - oauth token string without Bearer prefix
  :token-fn - a single-argument function that receives this opts and returns a token
  :client-cert/:ca-cert/:client-key - string filepath indicating certificates and
    key files to configure client certificate

  Example:
  (client \"https://kubernetes.docker.internal:6443\"
           {:basic-auth {:username \"admin\"
                         :password \"1234\"}})"
  [host opts]
  (let [k8s (pascal-case-routes
              (martian/bootstrap-swagger host (swagger/read)
                                         {:interceptors (concat [(interceptors.raise/new opts)
                                                                 (interceptors.auth/new opts)]
                                                                martian-httpkit/default-interceptors)}))]
    (assoc k8s
      ::api-group-list @(martian/response-for k8s :GetApiVersions))))

(defn extend-client
  "Extend a Kubernetes Client to support CustomResourceDefinitions

  Example:
  (extend-client k8s {:api \"tekton.dev\" :version \"v1alpha1\"})"
  [k8s {:keys [api version] :as extension-api}]
  (let [api-resources @(martian/response-for k8s :GetArbitraryApiResources
                                             {:api     api
                                              :version version})
        crds @(martian/response-for k8s :ListApiextensionsV1beta1CustomResourceDefinition)]
    (pascal-case-routes
      (update k8s
              :handlers #(concat % (martian.swagger/swagger->handlers (crd/swagger-from extension-api api-resources crds)))))))

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

(defn entities [k8s]
  (->> (:handlers k8s)
       (group-by (comp :x-kubernetes-group-version-kind :swagger-definition))
       (map (fn [[k v]] [(keyword (:kind k)) (map :route-name v)]))
       (into {})))

(defn actions [k8s kind]
  (->> (:handlers k8s)
       (filter (fn [handler] (= (keyword kind) (handler-kind handler))))
       (map (comp keyword :x-kubernetes-action :swagger-definition))
       (remove nil?)
       set
       not-empty))

(def versions ["v2"
               "v2beta2"
               "v2beta1"
               "v2alpha1"
               "v1"
               "v1beta1"
               "v1alpha1"])

(defn ^:private swagger-definition-for-route [k8s route-name]
  (->> (:handlers k8s)
       (misc/find-first #(= route-name (:route-name %)))
       :swagger-definition))

(defn explore-kind [k8s kind]
  (->> (martian/explore k8s)
       (filter (fn [[route-name _]]
                 (= (keyword kind)
                    (-> (swagger-definition-for-route k8s route-name)
                        :x-kubernetes-group-version-kind
                        :kind
                        keyword)))) vec))

(defn explore
  ([{:keys [handlers]}]
   (->> (group-by handler-kind handlers)
        (mapv (fn [[kind handlers]]
                (vec (cons (keyword kind) (mapv (juxt handler-action :summary) handlers)))))))
  ([k8s kind]
   (->> (explore k8s)
        (misc/find-first #(= kind (first %)))
        vec)))

(defn version-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :version))

(defn group-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :group))

(defn sort-by-version
  [k8s route-names]
  (sort-by (fn [route-name]
             (misc/first-index-of #(= % (version-of k8s route-name))
                                  versions)) route-names))

(defn choose-latest-version
  [k8s route-names]
  (first (sort-by-version k8s route-names)))

(defn core-versions [k8s]
  (mapv
    #(hash-map :name ""
               :versions [{:groupVersion % :version %}]
               :preferredVersion {:groupVersion % :version %})
    (:versions @(martian/response-for k8s :GetCoreApiVersions))))

(defn choose-preffered-version [k8s route-names]
  (misc/find-first
    (fn [route]
      (some #(and (= (:name %) (group-of k8s route))
                  (= (:version (:preferredVersion %)) (version-of k8s route)))
            (concat (:groups (::api-group-list k8s))
                    (core-versions k8s))))
    route-names))

(defn find-preferred-action [k8s search-params]
  (->> (find-action k8s search-params)
       (filter (fn [x] (not (string/ends-with? (name x) "Status"))))
       ((partial choose-preffered-version k8s))))

(defn invoke
  [k8s {:keys [request] :as params}]
  (if-let [action (find-preferred-action k8s (dissoc params :request))]
    @(martian/response-for k8s action (or request {}))
    (throw (ex-info "Could not find action" {:search (dissoc params :request)}))))

(defn request
  [k8s {:keys [request] :as params}]
  (if-let [action (find-preferred-action k8s (dissoc params :request))]
    (martian/request-for k8s action (or request {}))
    (throw (ex-info "Could not find action" {:search (dissoc params :request)}))))

(defn info
  [k8s {:keys [request] :as params}]
  (martian/explore k8s (find-preferred-action k8s (dissoc params :request))))


