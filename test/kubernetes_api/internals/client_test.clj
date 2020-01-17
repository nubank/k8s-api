(ns kubernetes-api.internals.client-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.internals.client :as internals.client]
            [matcher-combinators.matchers :as m]))

(deftest pascal-case-routes-test
  (is (= {:handlers [{:route-name :FooBar}
                     {:route-name :DuDuduEdu}]}
         (internals.client/pascal-case-routes {:handlers [{:route-name :foo-bar}
                                                          {:route-name :du-dudu-edu}]}))))

(deftest swagger-definition-for-route-test
  (is (= 'swagger-definition
         (internals.client/swagger-definition-for-route {:handlers [{:route-name         :FooBar
                                                                     :swagger-definition 'swagger-definition}
                                                                    {:route-name         :FooBarBaz
                                                                     :swagger-definition 'other-swagger-definition}]}
                                                        :FooBar))))

(deftest handler-functions-test
  (let [handler {:route-name :CreateV1NamespacedDeployment
                 :swagger-definition {:x-kubernetes-action :create
                                      :x-kubernetes-group-version-kind {:group ""
                                                                        :version "v1"
                                                                        :kind "Deployment"}}}]
    (is (= :create (internals.client/handler-action handler)))
    (is (= "" (internals.client/handler-group handler)))
    (is (= "v1" (internals.client/handler-version handler)))
    (is (= :Deployment (internals.client/handler-kind handler)))))

(deftest all-namespaces-route?-test
  (is (internals.client/all-namespaces-route? :GetDeploymentForAllNamespaces))
  (is (not (internals.client/all-namespaces-route? :GetDeployment))))

(deftest scale-resource-test
  (is (= "Deployment" (internals.client/scale-resource :PatchNamespacedDeploymentScale)))
  (is (= "ReplicaSet" (internals.client/scale-resource :PatchNamespacedReplicaSetScale))))

(deftest kind-test
  (is (= :Deployment
         (internals.client/kind {:route-name         :CreateV1NamespacedDeployment
                                 :swagger-definition {:x-kubernetes-group-version-kind {:kind "Deployment"}}})))
  (is (= :Deployment/Scale
         (internals.client/kind {:route-name         :CreateAutoscalingNamespacedDeploymentScale
                                 :swagger-definition {:x-kubernetes-group-version-kind {:kind "Scale"}}})))
  (is (= :Deployment/Status
         (internals.client/kind {:route-name         :CreateV1NamespacedDeploymentStatus
                                 :swagger-definition {:x-kubernetes-group-version-kind {:kind "Deployment"}}}))))

(deftest action-test
  (is (= :create (internals.client/action {:route-name :ItDoesntMatter
                                           :swagger-definition {:x-kubernetes-action "create"}})))
  (is (= :list-all (internals.client/action {:route-name :ReadXForAllNamespaces
                                             :swagger-definition {:x-kubernetes-action "list"}})))
  (testing "Binding special case"
    (is (= :pod/create (internals.client/action {:route-name         :CreateFooNamespacedPodBinding
                                                 :swagger-definition {:x-kubernetes-action "create"}}))))
  (testing "Connect special case"
    (is (= :connect.with-path/head (internals.client/action {:route-name         :ConnectFooProxyWithPath
                                                             :method             "head"
                                                             :swagger-definition {:x-kubernetes-action "connect"}})))
    (is (= :connect.with-path/post (internals.client/action {:route-name         :ConnectFooProxyWithPath
                                                             :method             "post"
                                                             :swagger-definition {:x-kubernetes-action "connect"}})))
    (is (= :connect/head (internals.client/action {:route-name         :ConnectFooProxy
                                                   :method             "head"
                                                   :swagger-definition {:x-kubernetes-action "connect"}})))
    (is (= :connect/post (internals.client/action {:route-name         :ConnectFooProxy
                                                   :method             "post"
                                                   :swagger-definition {:x-kubernetes-action "connect"}})))
    (is (= :connect/head (internals.client/action {:route-name         :ConnectFoo
                                                   :method             "head"
                                                   :swagger-definition {:x-kubernetes-action "connect"}})))
    (is (= :connect/post (internals.client/action {:route-name         :ConnectFoo
                                                   :method             "post"
                                                   :swagger-definition {:x-kubernetes-action "connect"}}))))
  (testing "Approval certificate patch"
    (is (= :replace-approval (internals.client/action {:route-name         :ReplaceCertificatesFooCertificateSigningRequestApproval
                                                       :swagger-definition {:x-kubernetes-action "put"}}))))
  (testing "Pod misc"
    (is (= :misc/logs (internals.client/action {:route-name :ReadV1NamespacedPodLog})))
    (is (= :misc/finalize (internals.client/action {:route-name :ReplaceV1NamespaceFinalize})))))

(def example-k8s {:handlers [{:route-name         :CreateV1alpha1Orange
                              :swagger-definition {:x-kubernetes-action             "create"
                                                   :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                     :version "v1alpha1"
                                                                                     :kind    "Orange"}}}
                             {:route-name         :ReplaceV1alpha1Orange
                              :swagger-definition {:x-kubernetes-action             "update"
                                                   :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                     :version "v1alpha1"
                                                                                     :kind    "Orange"}}}
                             {:route-name         :DeleteV1alpha1Orange
                              :swagger-definition {:x-kubernetes-action             "delete"
                                                   :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                     :version "v1alpha1"
                                                                                     :kind    "Orange"}}}
                             {:route-name         :GetV1alpha1Orange
                              :swagger-definition {:x-kubernetes-action             "get"
                                                   :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                     :version "v1alpha1"
                                                                                     :kind    "Orange"}}}
                             {:route-name         :ListV1alpha1Orange
                              :swagger-definition {:x-kubernetes-action             "list"
                                                   :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                     :version "v1alpha1"
                                                                                     :kind    "Orange"}}}]})

(deftest find-route-test
  (is (= [:CreateV1alpha1Orange :ReplaceV1alpha1Orange :DeleteV1alpha1Orange :GetV1alpha1Orange :ListV1alpha1Orange]
         (internals.client/find-route example-k8s {:kind :Orange})))
  (is (= [:GetV1alpha1Orange]
         (internals.client/find-route example-k8s {:kind :Orange
                                                   :action :get}))))

(deftest kubernetes-info-of-route-test
  (is (= "v1alpha1" (internals.client/version-of example-k8s :DeleteV1alpha1Orange)))
  (is (= :Orange (internals.client/kind-of example-k8s :DeleteV1alpha1Orange)))
  (is (= "fruits" (internals.client/group-of example-k8s :DeleteV1alpha1Orange))))

(deftest core-versions-test
  (is (match? [{:name             ""
                :versions         [{:groupVersion "v1" :version "v1"}]
                :preferredVersion {:groupVersion "v1" :version "v1"}}]
              (internals.client/core-versions {:kubernetes-api.core/core-api-versions {:versions ["v1"]}}))))

(deftest all-versions-test
  (is (match? (m/in-any-order [{:preferredVersion {:groupVersion "v1" :version "v1"}}
                               {:preferredVersion {:groupVersion "apps/v1alpha1" :version "v1alpha1"}}])
              (internals.client/all-versions {:kubernetes-api.core/core-api-versions {:versions ["v1"]}
                                              :kubernetes-api.core/api-group-list {:groups [{:name             "apps"
                                                                                             :versions         [{:groupVersion "apps/v1alpha1" :version "v1alpha1"}]
                                                                                             :preferredVersion {:groupVersion "apps/v1alpha1" :version "v1alpha1"}}]}}))))

(deftest find-preferred-route-test
  (is (= :CreateV1alpha2Orange
         (internals.client/find-preferred-route
          {:kubernetes-api.core/core-api-versions {:versions ["v1"]}
           :kubernetes-api.core/api-group-list    {:groups [{:name             "fruits"
                                                             :versions         [{:groupVersion "fruits/v1alpha1" :version "v1alpha1"}
                                                                                {:groupVersion "fruits/v1alpha2" :version "v1alpha2"}]
                                                             :preferredVersion {:groupVersion "apps/v1alpha2" :version "v1alpha2"}}]}
           :handlers                              [{:route-name         :CreateV1alpha1Orange
                                                    :swagger-definition {:x-kubernetes-action             "create"
                                                                         :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                                           :version "v1alpha1"
                                                                                                           :kind    "Orange"}}}
                                                   {:route-name         :CreateV1alpha2Orange
                                                    :swagger-definition {:x-kubernetes-action             "create"
                                                                         :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                                           :version "v1alpha2"
                                                                                                           :kind    "Orange"}}}]}
          {:kind   :Orange
           :action :create}))))

(deftest preffered-version?-test
  (let [k8s {:kubernetes-api.core/core-api-versions {:versions ["v1"]}
             :kubernetes-api.core/api-group-list    {:groups [{:name             "fruits"
                                                               :versions         [{:groupVersion "fruits/v1alpha1" :version "v1alpha1"}
                                                                                  {:groupVersion "fruits/v1alpha2" :version "v1alpha2"}]
                                                               :preferredVersion {:groupVersion "apps/v1alpha2" :version "v1alpha2"}}]}
             :handlers                              [{:route-name         :CreateV1alpha1Orange
                                                      :swagger-definition {:x-kubernetes-action             "create"
                                                                           :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                                             :version "v1alpha1"
                                                                                                             :kind    "Orange"}}}
                                                     {:route-name         :CreateV1alpha2Orange
                                                      :swagger-definition {:x-kubernetes-action             "create"
                                                                           :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                                             :version "v1alpha2"
                                                                                                             :kind    "Orange"}}}]}]
    (is (internals.client/preffered-version? k8s
                                             {:route-name         :CreateV1alpha2Orange
                                              :swagger-definition {:x-kubernetes-action             "create"
                                                                   :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                                     :version "v1alpha2"
                                                                                                     :kind    "Orange"}}}))
    (is (not (internals.client/preffered-version? k8s
                                                  {:route-name         :CreateV1alpha1Orange
                                                   :swagger-definition {:x-kubernetes-action             "create"
                                                                        :x-kubernetes-group-version-kind {:group   "fruits"
                                                                                                          :version "v1alpha1"
                                                                                                          :kind    "Orange"}}})))))
