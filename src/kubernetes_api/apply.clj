(ns kubernetes-api.apply
  (:require [kubernetes-api.core :as k8s]
            [yaml.core :as yaml]))

(defn apply-file [client filepath])

(comment

  (def c (k8s/client "https://kubernetes.docker.internal:6443"
                     (let [home "/Users/rafaeleal/.kube"]
                       {:ca-cert     (str home "/ca-docker.crt")
                        :client-cert (str home "/client-cert.pem")
                        :client-key  (str home "/client-java.key")})))

  (k8s/find-action c {:kind :PodSecurityPolicy
                      :action :post}) (->> c
                                           :handlers
                                           (map :swagger-definition)
                                           (map :x-kubernetes-group-version-kind)
                                           (map :version)
                                           (remove nil?)
                                           distinct
                                           sort) (map (fn [x]
                                                        (k8s/find-preferred-action c
                                                                                   {:kind   (keyword x)
                                                                                    :action :post}))
                                                      (distinct (map :kind
                                                                     (remove nil?
                                                                             (yaml/from-file "/Users/rafaeleal/Downloads/release.yaml" true))))))
