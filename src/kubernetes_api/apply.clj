(ns kubernetes-api.apply
  (:require [yaml.core :as yaml]
            [kubernetes-api.core :as k8s]))


(defn apply-file [client filepath])


(comment

  (def c (k8s/client "https://kubernetes.docker.internal:6443"
                     (let [home "/Users/rafaeleal/.kube"]
                       {:ca-cert     (str home "/ca-docker.crt")
                        :client-cert (str home "/client-cert.pem")
                        :client-key  (str home "/client-java.key")})))

  (k8s/find-action c {:kind :PodSecurityPolicy
                      :action :post})



  (map (fn [x]
         (k8s/find-preffered-action c {:kind (keyword x)
                             :action :get}))
       (map :kind
            (remove nil?
                    (yaml/from-file "/Users/rafaeleal/Downloads/release.yaml" true)))))
