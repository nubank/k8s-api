(ns kubernetes-api.core
  (:require [martian.core :as martian]
            [martian.httpkit :as martian-httpkit]
            [cheshire.core :as json]
            [schema.core :as s]
            [camel-snake-kebab.core :as csk]
            [less.awful.ssl :as ssl]
            clojure.data
            clojure.set
            [clojure.walk :as walk]
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

(defn fix-swagger [swagger]
  (-> swagger
      fix-description))

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

(defn find-preffered-action [k8s search-params]
  (->> (find-action k8s search-params)
       (filter (fn [x] (not (string/ends-with? (name x) "Status"))))))



(comment


  (clojure.data/diff {} {:foo 42})

  (def home "/Users/rafaeleal/.kube")
  (->> (:handlers c)
       (filter #(= (:route-name %) :ReadApiextensionsV1beta1CustomResourceDefinition))
       first
       :swagger-definition
       :x-kubernetes-group-version-kind)

  (actions c :CustomResourceDefinition)

  (def c (client "https://kubernetes.docker.internal:6443"
                 {:ca-cert     (str home "/ca-docker.crt")
                  :client-cert (str home "/client-cert.pem")
                  :client-key  (str home "/client-java.key")}))

  (martian/explore c)

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
  )
