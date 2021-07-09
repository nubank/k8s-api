(ns kubernetes-api.core
  (:require [kubernetes-api.extensions.custom-resource-definition :as crd]
            [kubernetes-api.interceptors.auth :as interceptors.auth]
            [kubernetes-api.interceptors.raise :as interceptors.raise]
            [kubernetes-api.internals.client :as internals.client]
            [kubernetes-api.internals.martian :as internals.martian]
            [kubernetes-api.misc :as misc]
            [kubernetes-api.swagger :as swagger]
            [martian.core :as martian]
            [martian.httpkit :as martian-httpkit]
            martian.swagger))

(defn client
  "Creates a Kubernetes Client compliant with martian api and its helpers

  host - a string url to the kubernetes cluster

  Options:

  [Authentication]
  :basic-auth - a map with plain text username/password
  :token - oauth token string without Bearer prefix
  :token-fn - a single-argument function that receives this opts and returns a
               token
  :client-cert/:ca-cert/:client-key - string filepath indicating certificates
                                       and key files to configure client cert.
  :insecure? - ignore self-signed server certificates

  [Custom]
  :interceptors - additional interceptors to the martian's client

  Example:
  (client \"https://kubernetes.docker.internal:6443\"
           {:basic-auth {:username \"admin\"
                         :password \"1234\"}})"
  [host opts]
  (let [interceptors (concat [(interceptors.raise/new opts)
                              (interceptors.auth/new opts)]
                             (:interceptors opts)
                             martian-httpkit/default-interceptors)
        k8s          (internals.client/pascal-case-routes
                      (martian/bootstrap-swagger host
                                                 (or (swagger/from-api host opts)
                                                     (swagger/read))
                                                 {:interceptors interceptors}))]
    (assoc k8s
           ::api-group-list (internals.martian/response-for k8s :GetApiVersions)
           ::core-api-versions (internals.martian/response-for k8s :GetCoreApiVersions))))

(defn invoke
  "Invoke a action on kubernetes api

   Parameters:
    :kind - a keyword identifing a kubernetes entity
    :action - each entity can have different subset of action. Examples:
               :create :update :patch :list :get :delete :deletecollection
    :request - to check what is this use kubernetes-api.core/info function
   Example:
   (invoke k8s {:kind :Deployment
                :action :create
                :request {:namespace \"default\"
                          :body {:apiVersion \"v1\", ...}})"
  [k8s {:keys [request] :as params}]
  (if-let [action (internals.client/find-preferred-route k8s (dissoc params :request))]
    (internals.martian/response-for k8s action (or request {}))
    (throw (ex-info "Could not find action" {:search (dissoc params :request)}))))

(defn extend-client
  "Extend a Kubernetes Client to support CustomResourceDefinitions

  Example:
  (extend-client k8s {:api \"tekton.dev\" :version \"v1alpha1\"})"
  [k8s {:keys [api version] :as extension-api}]
  (let [api-resources (internals.martian/response-for k8s :GetArbitraryApiResources
                                                      {:api     api
                                                       :version version})
        crds (internals.martian/response-for k8s :ListApiextensionsV1beta1CustomResourceDefinition)]
    (internals.client/pascal-case-routes
     (update k8s
             :handlers #(concat % (martian.swagger/swagger->handlers
                                   (crd/swagger-from extension-api api-resources crds)))))))

(defn explore
  "Return a data structure with all actions performable on Kubernetes API,
   organized per kind and per action

   Examples:
   (explore k8s)
    => [[:Deployment
          [:get \"some description\"]
          ...]
        [:Service
          [:create \"other description\"]
          ...]]
   (explore k8s :Deployment)
    => [:Deployment
         [:create \"description\"]
         ...]"
  ([{:keys [handlers] :as k8s}]
   (->> (filter (partial internals.client/preffered-version? k8s) handlers)
        (group-by internals.client/kind)
        (map (fn [[kind handlers]]
               (vec (cons (keyword kind)
                          (mapv (juxt internals.client/action :summary) handlers)))))
        (sort-by (comp str first))
        vec))
  ([k8s kind]
   (->> (explore k8s)
        (misc/find-first #(= kind (first %)))
        vec)))

(defn request
  "Returns the map that is passed to org.httpkit.client/request function. Used
    mostly for debugging. For customizing this, use the :interceptors option
    while creating an client"
  [k8s {:keys [request] :as params}]
  (if-let [action (internals.client/find-preferred-route k8s (dissoc params :request))]
    (martian/request-for k8s action (or request {}))
    (throw (ex-info "Could not find action" {:search (dissoc params :request)}))))

(defn info
  "Returns everything on a specific action, including request and response
    schemas"
  [k8s params]
  (martian/explore k8s (internals.client/find-preferred-route k8s (dissoc params :request))))
