(ns kubernetes-api.extensions.custom-resource-definition-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.extensions.custom-resource-definition :as crd]))

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

