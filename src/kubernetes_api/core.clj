(ns kubernetes-api.core
  (:require [martian.core :as martian]
            [martian.httpkit :as martian-httpkit]
            [cheshire.core :as json]
            [schema.core :as s]
            [camel-snake-kebab.core :as csk]
            [less.awful.ssl :as ssl]
            clojure.set
            [clojure.walk :as walk]))


(defn- new-ssl-engine [{:keys [ca-cert client-cert client-key]}]
  (-> (ssl/ssl-context client-key client-cert ca-cert)
      ssl/ssl-context->engine))

(defn fix-description [swagger]
  (walk/postwalk (fn [x]
                   (if (:description x)
                     (assoc x :summary (:description x))
                     x))
                 swagger))

(defn map-vals [f m]
  (->> (map (fn [[k v]] [k (f v)]) m)
       (into {})))

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

(defn raise-interceptor [opts]
  {:name  ::raise
   :leave (fn [{:keys [request response] :as context}]
            (if (status-error? (:status response))
              (raise-exception response)
              (do
                (with-meta {:response (:body response)}
                           {:request request :response response}))))})

(defn read-swagger []
  (fix-swagger (json/parse-string (slurp "resources/swagger.json") true)))

(defn extract-path-parts [path]
  (->> (re-seq #"\{(\w+)\}" path)
       (map (comp keyword second))))

(defn path-schema [path]
  (->> (extract-path-parts path)
       (map #(vector % s/Str))
       (into {})))

(defn pascal-case-routes [c]
  (update c :handlers
          (fn [handlers]
            (mapv #(update % :route-name csk/->PascalCase) handlers))))

(defn client [host opts]
  (pascal-case-routes
    (martian/bootstrap-swagger host
                               (read-swagger)
                               {:interceptors (concat
                                                [(raise-interceptor opts)
                                                 (auth-interceptor opts)]
                                                martian-httpkit/default-interceptors)})))

(def home "/Users/rafaeleal/.kube")

(defn find-action [k8s {:keys [kind action] :as _search-params}]
  (->> k8s
       :handlers
       (filter (fn [handler]
                 (and (or (= kind (-> handler :swagger-definition :x-kubernetes-group-version-kind :kind)) (nil? kind))
                      (or (= action (-> handler :swagger-definition :x-kubernetes-action)) (nil? action)) )))
       (map :route-name)))


(comment
  (keys (read-swagger))
  (extract-path-parts "/foo/{namespace}/bar/{name}")

  (path-schema "/foo/{namespace}/bar/{name}")

  (def swag2 )
  (martian.schema/parameter-keys (map err parameter-schemas))
  (defn clt [ ] (client "https://kubernetes.docker.internal:6443"
                    {:ca-cert     (str home "/ca-docker.crt")
                     :client-cert (str home "/client-cert.pem")
                     :client-key  (str home "/client-java.key")}))

  (def c (client "https://kubernetes.docker.internal:6443"
                 {:ca-cert     (str home "/ca-docker.crt")
                  :client-cert (str home "/client-cert.pem")
                  :client-key  (str home "/client-java.key")}))

  (find-action c {:kind   "Deployment"
                  :action "get"})
  (enrich-handler err)
  (def swag (read-swagger))

  (count (map #'martian/enrich-handler (martian.swagger/swagger->handlers (read-swagger))))

  (keys ((keyword "/apis/batch/v1beta1/watch/namespaces/{namespace}/cronjobs/{name}") (:paths swag)))

  (binding [*print-length* 10]
    (prn (range 1000)))
 (set! *print-length* nil)
  (set! *print-level* nil)
  (:interceptors c)
  (second (count (:handlers c)))

  (map name (keys (:paths (read-swagger))))
  (mapcat vals (vals (:paths (read-swagger))))

  (count (:handlers (clt)))

  (new-ssl-engine (str home "/ca-docker.crt")
                  (str home "/client-cert.pem")
                  (str home "/client-java.key"))
  (martian/explore c)
  (martian/explore c :list-core-v1-namespaced-pod)
  (martian/explore c :PatchAppsV1NamespacedDaemonSet)
  (martian/request-for c :watch-batch-v-1beta-1-namespaced-cron-job {:namespace "default"})


(csk/->PascalCase :list_core_v_1_namespaced_pod)
  (csk/->kebab-case (csk/->PascalCase :watch-batch-v-1beta-1-namespaced-cron-job))

  (martian/request-for c :list-core-v-1-namespaced-pod {:namespace "default"})
  @(martian/response-for c :ListCoreV1NamespacedPod {:namespace "default"})

  (->> @(martian/response-for c :ListCoreV1NamespacedPod {:namespace "default"})
       :items
       (map (comp :resourceVersion :metadata)))


  (->> @(martian/response-for c :ReadAppsV1NamespacedDeployment {:namespace "default"
                                                                 :name "nginx-deployment"})
       ((comp :resourceVersion :metadata)))


  )


(comment

  (def c (client "https://kubernetes.docker.internal:6443"
                 {:ca-cert     (str home "/ca-docker.crt")
                  :client-cert (str home "/client-cert.pem")
                  :client-key  (str home "/client-java.key")}))

  (require 'kubernetes-api.listeners)

  (def ctx (kubernetes-api.listeners/new-context {:client c}))

  (kubernetes-api.listeners/schedule-with-delay-seconds (:executer ctx) (fn [] (prn 42)) 2)
  (kubernetes-api.listeners/register ctx
                                     {:kind "Deployment"
                                      :name "nginx-deployment"
                                      :namespace "default"}
                                     kubernetes-api.listeners/print-version)

  (keys ctx)
  (kubernetes-api.listeners/deregister ctx
                                       #uuid"72247a69-580f-4b7d-b182-3235460b6b5d")

  (.isCancelled (get-in @(:state ctx) [:listeners #uuid"72247a69-580f-4b7d-b182-3235460b6b5d" :task]))

  (kubernetes-api.listeners/status ctx #uuid"72247a69-580f-4b7d-b182-3235460b6b5d")
  )
