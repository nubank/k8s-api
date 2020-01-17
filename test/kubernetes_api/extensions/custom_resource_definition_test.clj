(ns kubernetes-api.extensions.custom-resource-definition-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.extensions.custom-resource-definition :as crd]
            [matcher-combinators.test]))

(deftest new-route-name-test
  (testing "list"
    (is (= "ListTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "list" "tekton.dev" "v1alpha1" "Namespaced" "Task" {})))
    (is (= "ListTektonDevV1alpha1TaskForAllNamespaces"
           (crd/new-route-name "list" "tekton.dev" "v1alpha1" "Namespaced" "Task" {:all-namespaces true}))))

  (testing "get"
    (is (= "ReadTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "get" "tekton.dev" "v1alpha1" "Namespaced" "Task" {}))))

  (testing "create"
    (is (= "CreateTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "create" "tekton.dev" "v1alpha1" "Namespaced" "Task" {}))))

  (testing "deletecollection"
    (is (= "DeleteTektonDevV1alpha1CollectionNamespacedTask"
           (crd/new-route-name "deletecollection" "tekton.dev" "v1alpha1" "Namespaced" "Task" {}))))

  (testing "delete"
    (is (= "DeleteTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "delete" "tekton.dev" "v1alpha1" "Namespaced" "Task" {}))))

  (testing "patch"
    (is (= "PatchTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "patch" "tekton.dev" "v1alpha1" "Namespaced" "Task" {}))))

  (testing "update"
    (is (= "ReplaceTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "update" "tekton.dev" "v1alpha1" "Namespaced" "Task" {}))))

  (testing "watch"
    (is (= "WatchTektonDevV1alpha1NamespacedTask"
           (crd/new-route-name "watch" "tekton.dev" "v1alpha1" "Namespaced" "Task" {})))
    (is (= "WatchTektonDevV1alpha1TaskList"
           (crd/new-route-name "watch" "tekton.dev" "v1alpha1" nil "TaskList" {})))
    (is (= "WatchTektonDevV1alpha1TaskListForAllNamespaces"
           (crd/new-route-name "watch" "tekton.dev" "v1alpha1" nil "TaskList" {:all-namespaces true})))))

(deftest swagger-from-test
  (testing "it generated CustomResourceDefinition paths"
    (is (match?
         {:paths {"/apis/tekton.dev/v1alpha1/tasks"
                  {:get {:operationId "ListTektonDevV1alpha1TaskForAllNamespaces"}}

                  "/apis/tekton.dev/v1alpha1/namespaces/{namespace}/tasks"
                  {:get    {:operationId "ListTektonDevV1alpha1NamespacedTask"}
                   :post   {:operationId "CreateTektonDevV1alpha1NamespacedTask"}
                   :delete {:operationId "DeleteTektonDevV1alpha1CollectionNamespacedTask"}}

                  "/apis/tekton.dev/v1alpha1/namespaces/{namespace}/tasks/{name}"
                  {:get    {:operationId "ReadTektonDevV1alpha1NamespacedTask"}
                   :patch  {:operationId "PatchTektonDevV1alpha1NamespacedTask"}
                   :put    {:operationId "ReplaceTektonDevV1alpha1NamespacedTask"}
                   :delete {:operationId "DeleteTektonDevV1alpha1NamespacedTask"}}}}
         (crd/swagger-from
          {:api     "tekton.dev"
           :version "v1alpha1"}
          {:kind         "APIResourceList"
           :groupVersion "tekton.dev/v1alpha1"
           :resources    [{:name               "tasks",
                           :singularName       "task",
                           :namespaced         true,
                           :kind               "Task",
                           :verbs              ["delete" "deletecollection" "get" "list" "patch" "create" "update" "watch"],
                           :categories         ["all" "tekton-pipelines"],
                           :storageVersionHash "Vwu99D/K4xM="}]}
          {:kind  "CustomResourceDefinitionList"
           :items [{:spec {:group        "tekton.dev"
                           :version      "v1alpha1"
                           :names        {:singular   "task"
                                          :plural     "tasks"
                                          :kind       "Task"
                                          :listKind   "TaskList"
                                          :categories ["all" "tekton-pipelines"]}
                           :scope        "Namespaced"
                           :subresources {:status {}}
                           :versions     [{:name    "v1alpha1"
                                           :served  true
                                           :storage true
                                           :schema  {:openAPIV3Schema
                                                     {:type "object"}}}]}}]})))))
