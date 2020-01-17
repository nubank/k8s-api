(ns kubernetes-api.extensions.custom-resource-definition
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as string]
            [kubernetes-api.misc :as misc]))

(defn new-route-name
  [verb group version scope kind {:keys [all-namespaces]}]
  (letfn [(prefix [k8s-verb]
            (case k8s-verb
              "update" "replace"
              "get" "read"
              "deletecollection" "delete"
              k8s-verb))
          (suffix [k8s-verb]
                  (case k8s-verb
                    "deletecollection" "collection"
                    nil))]
    (csk/->PascalCase (string/join "_" (->> [(prefix verb)
                                             group
                                             version
                                             (suffix verb)
                                             (when-not all-namespaces scope)
                                             (csk/->snake_case kind)
                                             (when all-namespaces :for_all_namespaces)]
                                            (remove nil?)
                                            (map name)))
                      :separator #"[_\.]")))

(def k8s-verb->http-verb
  {"delete"           "delete"
   "deletecollection" "delete"
   "get"              "get"
   "list"             "get"
   "patch"            "patch"
   "create"           "post"
   "update"           "put"
   "watch"            "get"})

(def k8s-verb->summary-template
  {"delete"           "delete a %s"
   "deletecollection" "delete collection of %s"
   "get"              "read the specified %s"
   "list"             "list of watch objects of kind %s"
   "patch"            "partially update the specified %s"
   "create"           "create a %s"
   "update"           "replace the specified %s"
   "watch"            "deprecated: use the 'watch' parameter with a list operation instead"})

(defn method [k8s-verb {:keys [api version]} {{:keys [scope versions names]} :spec :as _crd} opts]
  (let [content-types ["application/json"
                       "application/yaml"
                       "application/vnd.kubernetes.protobuf"]
        crd-version   (misc/find-first #(= (:name %) version) versions)
        kind         (:kind names)]
    {(keyword (k8s-verb->http-verb k8s-verb))
     (misc/assoc-some {:summary                         (format (k8s-verb->summary-template k8s-verb) kind)
                       :operationId                     (new-route-name k8s-verb api version scope kind opts)
                       :consumes                        content-types
                       :produces                        content-types
                       :responses                       {"200" (misc/assoc-some
                                                                {:description "OK"}
                                                                :schema (-> crd-version :schema :openAPIV3Schema))
                                                         "401" {:description "Unauthorized"}}
                       :x-kubernetes-action             k8s-verb
                       :x-kubernetes-group-version-kind {:group   api
                                                         :version version
                                                         :kind    kind}}
                      :parameters (when (#{"create" "update"} k8s-verb)
                                    [{:in       "body"
                                      :name     "body"
                                      :required true
                                      :schema   {:type "object"}}]))}))

(defn top-level [extension-api]
  (str "/apis/" (:api extension-api) "/" (:version extension-api)))

(defn top-resource [extension-api resource-name]
  (str (top-level extension-api) "/" resource-name))

(defn namespaced-route [extension-api resource-name]
  (str (top-level extension-api) "/namespaces/{namespace}/" resource-name))

(defn named-route [extension-api resource-name]
  (str (namespaced-route extension-api resource-name) "/{name}"))

(defn routes [k8s-verb extension-api {resource-name :name :as resource} crd]
  (cond
    (#{:get :delete :patch :update} (keyword k8s-verb))
    {(named-route extension-api resource-name) (method k8s-verb extension-api crd {})}

    (#{:create :deletecollection} (keyword k8s-verb))
    {(namespaced-route extension-api resource-name) (method k8s-verb extension-api crd {})}

    (= :watch (keyword k8s-verb))
    {}                                                      ; TODO: Fix Watch requests

    (= :list (keyword k8s-verb))
    {(top-resource extension-api resource-name)        (method k8s-verb extension-api crd {:all-namespaces true})
     (namespaced-route extension-api resource-name) (method k8s-verb extension-api crd {})}))

(defn add-path-params [extension-api {resource-name :name} paths]
  (into {}
        (map (fn [[path methods]]
               [path (misc/assoc-some methods
                                      :parameters (cond
                                                    (string/starts-with? (name path) (named-route extension-api resource-name))
                                                    [{:in     "path"
                                                      :name   "name"
                                                      :required true
                                                      :schema {:type "string"}}
                                                     {:in     "path"
                                                      :name   "namespace"
                                                      :required true
                                                      :schema {:type "string"}}]
                                                    (string/starts-with? (name path) (namespaced-route extension-api resource-name))
                                                    [{:in     "path"
                                                      :name   "namespace"
                                                      :required true
                                                      :schema {:type "string"}}]
                                                    :else nil))]) paths)))

(defn single-resource-swagger [extension-api {:keys [verbs] :as resource} crd]
  (->> (mapcat #(routes % extension-api resource crd) verbs)
       (group-by first)
       (misc/map-vals (fn [x] (into {} (map second x))))
       (into {})
       (add-path-params extension-api resource)))

(defn status-subresource? [resource]
  (string/includes? (:name resource) "/status"))

(defn scale-subresource? [resource]
  (string/includes? (:name resource) "/scale"))

(defn subresource? [resource]
  (or (status-subresource? resource)
      (scale-subresource? resource)))

(defn top-level-resources [resources]
  (let [top-levels (remove subresource? resources)
        subresources (filter subresource? resources)]
    (mapv (fn [resource]
            (misc/assoc-some resource
                             :status? (some #(and (status-subresource? %)
                                                  (string/starts-with? (:name %) (:name resource)))
                                            subresources)
                             :scale? (some #(and (scale-subresource? %)
                                                 (string/starts-with? (:name %) (:name resource)))
                                           subresources))) top-levels)))

(defn swagger-from [extention-api
                    {:keys [resources] :as _api-resources}
                    {:keys [items] :as crds}]
  {:paths (into {} (mapcat (fn [resource] (single-resource-swagger extention-api resource (misc/find-first (fn [{{:keys [group version names]} :spec}] (and (= (:api extention-api) group) (= (:version extention-api) version) (= (:kind resource) (:kind names)))) items))) (top-level-resources resources)))})
