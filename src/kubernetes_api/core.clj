(ns kubernetes-api.core
  (:require [martian.core :as martian]
            [martian.httpkit :as martian-httpkit]
            [cheshire.core :as json]
            [schema.core :as s]
            [less.awful.ssl :as ssl]
            [clojure.walk :as walk]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(defn- new-ssl-engine [ca-cert client-cert client-key]
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

(defn update-methods [methods]
  (map-vals (fn [{:keys [parameters] :as path-obj}]
              (assoc path-obj :parameters (conj parameters {:foo 42}))) methods))

(defn fix-path-params [swagger]
  (update swagger :paths
          (fn [paths]
            (map-vals update-methods paths))))

(defn fix-swagger [swagger]
  (-> swagger
      fix-description
      fix-path-params))

(defn client-cert-interceptor [ca-cert client-cert client-key]
  {:name  ::client-certificate
   :enter (fn [context]
            (assoc-in context [:request :sslengine] (new-ssl-engine ca-cert client-cert client-key)))})

(defn read-swagger []
  (fix-swagger (json/parse-string (slurp "resources/swagger.json") true)))

(defn extract-path-parts [path]
  (->> (re-seq #"\{(\w+)\}" path)
       (map (comp keyword second))))

(defn path-schema [path]
  (->> (extract-path-parts path)
       (map #(vector % s/Str))
       (into {})))



(defn client [host opts]
  (martian/bootstrap-swagger host
                             (read-swagger)
                             {:interceptors (concat martian-httpkit/default-interceptors
                                                    [(client-cert-interceptor (str home "/ca-docker.crt")
                                                                              (str home "/client-cert.pem")
                                                                              (str home "/client-java.key"))])}))

(comment
  (keys (read-swagger))
  (extract-path-parts "/foo/{namespace}/bar/{name}")

  (path-schema "/foo/{namespace}/bar/{name}")

  (def swag2 )

  (def c (martian/bootstrap-swagger "https://kubernetes.docker.internal:6443"
                                    (read-swagger)
                                    {:interceptors (concat martian-httpkit/default-interceptors
                                                           [(client-cert-interceptor (str home "/ca-docker.crt")
                                                                                     (str home "/client-cert.pem")
                                                                                     (str home "/client-java.key"))])}))


  (def swag (read-swagger))

  (keys ((keyword "/apis/batch/v1beta1/watch/namespaces/{namespace}/cronjobs/{name}") (:paths swag)))

  (binding [*print-length* 10]
    (prn (range 1000)))
 (set! *print-length* nil)
  (set! *print-level* nil)
  (second (:handlers c))

  (mapcat vals (vals (:paths (read-swagger))))


  (def home "/Users/rafaeleal/.kube")
  (new-ssl-engine (str home "/ca-docker.crt")
                  (str home "/client-cert.pem")
                  (str home "/client-java.key"))
  (martian/explore c)
  (martian/explore c :list-core-v-1-namespaced-pod)



  (keep (fn [i] (when (<= i 2) (+ 1 i))) [1 2 3])

  (martian/request-for c :watch-batch-v-1beta-1-namespaced-cron-job {:namespace "default"})

  (martian/request-for c :list-core-v-1-namespaced-pod {})
  @(martian/response-for c :list-core-v-1-namespaced-pod {:namespace "default"}))
