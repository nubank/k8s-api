(ns kubernetes-api.core-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.core :refer :all]))

(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))

#_(deftest swagger-from-test
    (testing "idk"
      (is (= {"/apis/tekton.dev/v1alpha1/tasks"
              {:get {:operationId "ListTektonDevV1alpha1TaskForAllNamespaces"}}

              "/apis/tekton.dev/v1alpha1/namespaces/{namespace}/tasks"
              {:get    {:operationId "ListTektonDevV1alpha1NamespacedTask"}
               :post   {:operationId "CreateTektonDevV1alpha1NamespacedTask"}
               :delete {:operationId "DeleteTektonDevV1alpha1CollectionNamespacedTask"}}

              "/apis/tekton.dev/v1alpha1/namespaces/{namespace}/tasks/{name}"
              {:get    {:operationId "ReadTektonDevV1alpha1NamespacedTask"}
               :patch  {:operationId "PatchTektonDevV1alpha1NamespacedTask"}
               :put    {:operationId "ReplaceTektonDevV1alpha1NamespacedTask"}
               :delete {:operationId "DeleteTektonDevV1alpha1NamespacedTask"}}}
             (swagger-from
              {:api "tekton.dev"
               :version "v1alpha1"}
              {:kind "APIResourceList"
               :groupVersion "tekton.dev/v1alpha1"
               :resources [{:name "tasks",
                            :singularName "task",
                            :namespaced true,
                            :kind "Task",
                            :verbs ["delete" "deletecollection" "get" "list" "patch" "create" "update" "watch"],
                            :categories ["all" "tekton-pipelines"],
                            :storageVersionHash "Vwu99D/K4xM="}]}
              {:kind "CustomResourceDefinitionList"
               :items [{:spec {:group "tekton.dev"
                               :version "v1alpha1"
                               :names {:singular "task"
                                       :plural "tasks"
                                       :kind "Task"
                                       :listKind "TaskList"
                                       :categories ["all" "tekton-pipelines"]}
                               :scope "Namespaced"
                               :subresources {:status {}}
                               :versions [{:name "v1alpha1"
                                           :served true
                                           :storage true
                                           :schema {:openAPIV3Schema
                                                    {:type "object"}}}]}}]})))))

#_(deftest routes-test
    (routes "list"
            {:api "tekton.dev"
             :version "v1alpha1"}
            {:name "tasks"}
            {:spec {:names {:kind "Task"}
                    :scope "Namespaced"}}))

#_(deftest method-test
    (method "list"
            {:api     "tekton.dev"
             :version "v1alpha1"}
            {:spec {:names {:kind "Task"}
                    :scope "Namespaced"}}
            {}))
