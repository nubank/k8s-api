(ns kubernetes-api.core
  (:require [martian.core :as martian]
            [martian.httpkit :as martian-httpkit]
            martian.swagger
            [cheshire.core :as json]
            [schema.core :as s]
            [camel-snake-kebab.core :as csk]
            [less.awful.ssl :as ssl]
            clojure.data
            clojure.set
            [clojure.walk :as walk]
            [kubernetes-api.misc :as misc]
            [clojure.string :as string]))


(defn- new-ssl-engine [{:keys [ca-cert client-cert client-key]}]
  (-> (ssl/ssl-context client-key client-cert ca-cert)
      ssl/ssl-context->engine))

(defn fix-description [swagger]
  (walk/postwalk (fn [x]
                   (if (:description x)
                     (assoc x :summary (:description x))
                     x))
                 swagger))

(def arbitrary-api-resources-route
  {(keyword "/apis/{api}/{version}/") {:get        {:consumes    ["application/json"
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

(defn fix-swagger [swagger]
  (-> swagger
      fix-description
      (add-some-routes {} arbitrary-api-resources-route)))

(defn client-certs? [{:keys [ca-cert client-cert client-key]}]
  (every? some? [ca-cert client-cert client-key]))

(defn basic-auth? [{:keys [username password]}]
  (every? some? [username password]))

(defn basic-auth [{:keys [username password]}]
  (str username ":" password))

(defn token? [{:keys [token]}]
  (some? token))

(defn token-fn? [{:keys [token-fn]}]
  (some? token-fn))

(defn request-auth-params [{:keys [token-fn] :as opts}]
  (merge
    {:insecure? true}
    (cond
      (basic-auth? opts) {:basic-auth (basic-auth opts)}
      (token? opts) {:oauth-token (:token opts)}
      (token-fn? opts) {:oauth-token (token-fn opts)}
      (client-certs? opts) {:insecure? false
                            :sslengine (new-ssl-engine opts)})))

(defn auth-interceptor [opts]
  {:name  ::authentication
   :enter (fn [context]
            (update context :request #(merge % (request-auth-params opts))))})

(defn status-error? [status]
  (>= status 400))

(def ^:private error-type->status-code
  {:bad-request                   400
   :invalid-input                 400
   :unauthorized                  401
   :payment-required              402
   :forbidden                     403
   :not-found                     404
   :method-not-allowed            405
   :not-acceptable                406
   :proxy-authentication-required 407
   :timeout                       408
   :conflict                      409
   :gone                          410
   :length-required               411
   :precondition-failed           412
   :payload-too-large             413
   :uri-too-long                  414
   :unsupported-media-type        415
   :range-not-satisfiable         416
   :expectation-failed            417
   :unprocessable-entity          422
   :locked                        423
   :upgrade-required              426
   :too-many-requests             429
   :server-error                  500
   :not-implemented               501
   :bad-gateway                   502
   :service-unavailable           503
   :gateway-timeout               504
   :http-version-not-supported    505})

(def status-code->error-type (clojure.set/map-invert error-type->status-code))

(defn raise-exception [{:keys [body status] :as _response}]
  (throw (ex-info "API-Server error"
                  {:type (status-code->error-type status)
                   :body body})))

(defn raise-interceptor [_]
  {:name  ::raise
   :leave (fn [{:keys [request response] :as _context}]
            (if (status-error? (:status response))
              (raise-exception response)
              (with-meta {:response (:body response)}
                         {:request request :response response})))})

(defn read-swagger []
  (fix-swagger (json/parse-string (slurp "resources/swagger.json") true)))

(defn extract-path-parts [path]
  (->> (re-seq #"\{(\w+)\}" path)
       (map (comp keyword second))))

(defn path-schema [path]
  (->> (extract-path-parts path)
       (map #(vector % s/Str))
       (into {})))

(defn pascal-case-routes [k8s]
  (update k8s :handlers
          (fn [handlers]
            (mapv #(update % :route-name csk/->PascalCase) handlers))))

(defn client [host opts]
  (pascal-case-routes
    (martian/bootstrap-swagger host (read-swagger) {:interceptors (concat [(raise-interceptor opts)
                                                                           (auth-interceptor opts)]
                                                                          martian-httpkit/default-interceptors)})))



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

(defn method [k8s-verb {:keys [api version]} {{:keys [scope versions names]} :spec :as _crd} opts]
  (let [content-types ["application/json"
                       "application/yaml"
                       "application/vnd.kubernetes.protobuf"]
        crd-version (misc/find-first #(= (:name %) version) versions)]
    (prn crd-version)
    {(keyword (k8s-verb->http-verb k8s-verb))
     {:operationId (new-route-name k8s-verb api version scope (:kind names) opts)
      :consumes    content-types
      :produces    content-types
      :responses   {"200" (misc/assoc-some
                            {:description "OK"}
                            :schema (-> crd-version :schema :openAPIV3Schema))
                    "401" {:description "Unauthorized"}}}}))

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

(defn swagger-from [extention-api
                    {:keys [resources] :as _api-resources}
                    {:keys [items] :as crds}]
  {:paths (->> (mapcat (fn [resource]
                         (single-resource-swagger extention-api resource (misc/find-first
                                                                           (fn [{{:keys [group version names]} :spec}]
                                                                             (and (= (:api extention-api) group)
                                                                                  (= (:version extention-api) version)
                                                                                  (= (:kind resource) (:kind names)))) items)))
                       resources)
               (into {}))})

(defn extend-client [k8s {:keys [api version] :as extension-api}]
  (let [api-resources @(martian/response-for k8s :GetArbitraryApiResources
                                             {:api     api
                                              :version version})
        crds @(martian/response-for k8s :ListApiextensionsV1beta1CustomResourceDefinition)]
    (pascal-case-routes
      (update k8s
              :handlers #(concat % (martian.swagger/swagger->handlers (swagger-from extension-api api-resources crds)))))))

(defn handler-kind [handler]
  (-> handler :swagger-definition :x-kubernetes-group-version-kind :kind keyword))

(defn handler-action [handler]
  (-> handler :swagger-definition :x-kubernetes-action keyword))

(defn find-action [k8s {:keys [kind action] :as _search-params}]
  (->> (:handlers k8s)
       (filter (fn [handler]
                 (and (or (= (keyword kind) (handler-kind handler)) (nil? kind))
                      (or (= (keyword action) (handler-action handler)) (nil? action)))))
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


(defn swagger-definition-for-route [k8s route-name]
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
                        keyword))))
       vec))

(defn version-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :version))

(defn sort-by-version
  [k8s route-names]
  (sort-by (fn [route-name]
             (misc/first-index-of #(= % (version-of k8s route-name))
                                  versions)) route-names))

(defn choose-latest-version
  [k8s route-names]
  (first (sort-by-version k8s route-names)))

(defn find-preffered-action [k8s search-params]
  (->> (find-action k8s search-params)
       (filter (fn [x] (not (string/ends-with? (name x) "Status"))))
       ((partial choose-latest-version k8s))))

(comment

  (new-route-name :get :tekton.dev :v1alpha1 :Namespaced :Task)

  (def home "/Users/rafaeleal/.kube")

  (def c (client "https://kubernetes.docker.internal:6443"
                 {:ca-cert     (str home "/ca-docker.crt")
                  :client-cert (str home "/client-cert.pem")
                  :client-key  (str home "/client-java.key")}))

  (martian/explore c)

  (def c2 (extend-client c {:api     "tekton.dev"
                     :version "v1alpha1"}))

  (martian/explore c)

  (misc/first-index-of #(= "v1" %) ["v2" "v1"])

  (sort-by (fn [v] (misc/first-index-of #(= % v) versions)) ["v1beta1" "v1" "v1alpha1"])

  (map (partial version-of c)  [:ReadRbacAuthorizationV1beta1ClusterRole
                                :ReadRbacAuthorizationV1ClusterRole
                                :ReadRbacAuthorizationV1alpha1ClusterRole])



  (choose-latest-version c [:ReadRbacAuthorizationV1beta1ClusterRole
                            :ReadRbacAuthorizationV1ClusterRole
                            :ReadRbacAuthorizationV1alpha1ClusterRole])


  (explore-kind c :CustomResourceDefinition)

  (explore-kind c :Deployment)


  (explore-kind c2 nil)
  (martian/explore c :ListApiextensionsV1CustomResourceDefinition)

  (martian/request-for c :GetApiextensionsV1beta1ApiResources)

  @(martian/response-for c :ListApiextensionsV1beta1CustomResourceDefinition)

  @(martian/response-for c :GetApiextensionsV1beta1ApiResources)

  @(org.httpkit.client/request
     (assoc (martian/request-for c :ListApiextensionsV1beta1CustomResourceDefinition)
       :url "https://kubernetes.docker.internal:6443/apis/tekton.dev/v1alpha1/tasks",))

  @(org.httpkit.client/request
     (assoc (martian/request-for c :ListApiextensionsV1beta1CustomResourceDefinition)
       :url "https://kubernetes.docker.internal:6443/apis/tekton.dev/v1alpha1/namespaces/default/tasks",))


  @(org.httpkit.client/request
     (assoc (martian/request-for c :ListApiextensionsV1beta1CustomResourceDefinition)
       :url "https://kubernetes.docker.internal:6443/apis/tekton.dev/v1alpha1",))

  @(martian/response-for c :ListApiextensionsV1beta1CustomResourceDefinition)

  (swagger-definition-for-route c :ReadRbacAuthorizationV1beta1ClusterRoleBinding)

  (->> (:handlers c)
       (misc/find-first #(= :ReadRbacAuthorizationV1beta1ClusterRoleBinding (:route-name %)))
       :swagger-definition)
  (clojure.data/diff {} {:foo 42})

  (explore-kind c nil)
  (martian/explore c :GetArbitraryApiResources)

  @(martian/response-for c :GetArbitraryApiResources {:api "tekton.dev"
                                                      :version "v1alpha1"})

  (->> (:handlers c)
       (filter #(= (:route-name %) :ReadApiextensionsV1beta1CustomResourceDefinition))
       first
       :swagger-definition
       :x-kubernetes-group-version-kind)

  (actions c :CustomResourceDefinition)

  (martian/explore c)

  (martian/explore c :GetApiVersions)


  @(martian/response-for c :GetApiVersions)
  @(martian/response-for c :GetCoreApiVersions)

  (martian/explore c :ReadApiextensionsV1CustomResourceDefinition)

  (entities c)

  (actions c :Pod)

  (find-action c {:kind   :Deployment
                  :action :get})

  (martian/request-for c :ReadAppsV1NamespacedDeployment {:namespace "default"
                                                          :name      "nginx-deployment"})




  @(martian/response-for c :ListCoreV1NamespacedPod {:namespace "default"})

  )


(comment

  (def c (client "https://kubernetes.docker.internal:6443"
                 {:ca-cert     (str home "/ca-docker.crt")
                  :client-cert (str home "/client-cert.pem")
                  :client-key  (str home "/client-java.key")}))

  (require '[kubernetes-api.listeners :as listeners])

  (def ctx (listeners/new-context {:client c}))

  (def list-id (listeners/register ctx
                                   {:kind      "Deployment"
                                    :name      "nginx-deployment"
                                    :namespace "default"}
                                   listeners/print-version))

  (listeners/deregister ctx list-id)

  (listeners/status ctx list-id)


  {(keyword "/apis/{api}/{version}/") {:get {:consumes ["application/json"
                                                        "application/yaml"
                                                        "application/vnd.kubernetes.protobuf"]
                                             :descriptiion "get available resources for arbitrary api"
                                             :operationId "GetArbitraryAPIResources"
                                             :produces ["application/json"
                                                        "application/yaml"
                                                        "application/vnd.kubernetes.protobuf"]
                                             :responses {"200" {:description "OK"
                                                                :schema {:$ref "#/definitions/io.k8s.apimachinery.pkg.apis.meta.v1.APIResourceList"}}
                                                         "401" {:description "Unauthorized"}}
                                             :schemes ["https"]}
                                       :parameters [{:in "path"
                                                     :name "api"
                                                     :schema {:type "string"}}
                                                    {:in "path"
                                                     :name "version"
                                                     :schema {:type "string"}}]}}

  )

;
;{    "/apis/{api}/{version}/": {
;                                "get": {
;                                        "consumes": [
;                                                     "application/json",
;                                                     "application/yaml",
;                                                     "application/vnd.kubernetes.protobuf"
;                                                     ],
;                                                  "description": "get available resources",
;                                        "operationId": "getAdmissionregistrationV1APIResources",
;                                        "produces": [
;                                                     "application/json",
;                                                     "application/yaml",
;                                                     "application/vnd.kubernetes.protobuf"
;                                                     ],
;                                        "responses": {
;                                                      "200": {
;                                                              "description": "OK",
;                                                                           "schema": {
;                                                                                      "$ref": "#/definitions/io.k8s.apimachinery.pkg.apis.meta.v1.APIResourceList"
;                                                                                      }
;                                                              },
;                                                           "401": {
;                                                                   "description": "Unauthorized"
;                                                                   }
;                                                      },
;                                        "schemes": [
;                                                    "https"
;                                                    ],
;                                        "tags": [
;                                                 "admissionregistration_v1"
;                                                 ]
;                                        }
;                                     "parameters": [
;                                                    {
;                                                     "in": "path",
;                                                         "name": "api",
;                                                     "schema": {"type": "string"}
;                                                     },
;                                                    {
;                                                     "in": "path"
;                                                     }
;                                                    ]
;                                },}
