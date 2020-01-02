(ns kubernetes-api.swagger
  (:refer-clojure :exclude [read])
  (:require [clojure.walk :as walk]
            [cheshire.core :as json]
            [clojure.string :as string]))


(defn remove-watch-endpoints
  "Watch endpoints doesn't follow the http1.1 specification, so it will not work
  with httpkit or similar.

  Related: https://github.com/kubernetes/kubernetes/issues/50857"
  [swagger]
  (update swagger :paths
          (fn [paths]
            (apply dissoc (concat [paths]
                                  (->> (keys paths)
                                       (filter #(string/includes? % "/watch"))))))))

(defn fix-k8s-verb
  "For some reason, the x-kubernetes-action given by the kubernetes api doesnt
  respect its on specification :shrug:

  More info: https://kubernetes.io/docs/reference/access-authn-authz/authorization/#determine-the-request-verb"
  [swagger]
  (walk/postwalk (fn [{:keys [x-kubernetes-action] :as form}]
                   (if x-kubernetes-action
                     (assoc form :x-kubernetes-action
                                 (case (keyword x-kubernetes-action)
                                   :post "create"
                                   :put "update"
                                   :watchlist "watch"
                                   x-kubernetes-action))
                     form))
                 swagger))


(defn fix-consumes
  "Some endpoints declares that consumes */* which is not true and it doesn't
  let us select the correct encoder"
  [swagger]
  (walk/postwalk (fn [{:keys [consumes] :as form}]
                   (if (= consumes ["*/*"])
                     (assoc form :consumes ["application/json"])
                     form))
                 swagger))

(defn add-summary [swagger]
  (walk/postwalk (fn [{:keys [description summary] :as form}]
                   (if description
                     (assoc form :summary (or summary description))
                     form))
                 swagger))

(def arbitrary-api-resources-route
  {"/apis/{api}/{version}/"
   {:get        {:consumes    ["application/json"
                               "application/yaml"
                               "application/vnd.kubernetes.protobuf"]
                 :summary     "get available resources for arbitrary api"
                 :operationId "GetArbitraryAPIResources"
                 :produces    ["application/json"
                               "application/yaml"
                               "application/vnd.kubernetes.protobuf"]
                 :responses   {"200" {:description "OK"
                                      :schema      {:$ref "#/definitions/io.k8s.apimachinery.pkg.apis.meta.v1.APIResourceList"}}
                               "401" {:description "Unauthorized"}}
                 :schemes     ["https"]}
    :parameters [{:in     "path"
                  :name   "api"
                  :schema {:type "string"}}
                 {:in     "path"
                  :name   "version"
                  :schema {:type "string"}}]}})

(defn add-some-routes
  [swagger new-definitions new-routes]
  (-> swagger
      (update :paths #(merge % new-routes))
      (update :definitions #(merge % new-definitions))))

(defn ^:private customized
  "Receives a kubernetes swagger, adds a description to the routes and some
  generic routes"
  [swagger]
  (-> swagger
      add-summary
      (add-some-routes {} arbitrary-api-resources-route)
      fix-k8s-verb
      fix-consumes
      remove-watch-endpoints))

(defn ^:private keyword-except-paths [s]
  (if (string/starts-with? s "/")
    s
    (keyword s)))

(defn read []
  (customized (json/parse-string (slurp "resources/swagger.json") keyword-except-paths)))