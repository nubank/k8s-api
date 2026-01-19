(ns kubernetes-api.core-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [kubernetes-api.core :as k8s]))

;; TODO

(deftest explore-test
  (testing "example k8s"
    (let [k8s-client {::k8s/core-api-versions {:versions ["v1"]}
                      ::k8s/api-group-list {:groups [{:name "fruits.group.dev"
                                                      :versions [{:groupVersion "fruits.group.dev/v1alpha2" :version "v1alpha2"}]
                                                      :preferredVersion {:groupVersion "fruits.group.dev/v1alpha2" :version "v1alpha2"}}
                                                     {:name "vegetables.group.dev"
                                                      :versions [{:groupVersion "vegetables.group.dev/v1alpha1" :version "v1alpha1"}]
                                                      :preferredVersion {:groupVersion "vegetables.group.dev/v1alpha1" :version "v1alpha1"}}]}
                      :handlers [{:route-name         :CreateV1alpha2Orange
                                  :summary "create an Orange"
                                  :swagger-definition {:x-kubernetes-action             "create"
                                                       :x-kubernetes-group-version-kind {:group   "fruits.group.dev"
                                                                                         :version "v1alpha2"
                                                                                         :kind    "Orange"}}}
                                 {:route-name         :UpdateV1alpha1Orange
                                  :summary "replace the specified Orange"
                                  :swagger-definition {:x-kubernetes-action             "update"
                                                       :x-kubernetes-group-version-kind {:group   "fruits.group.dev"
                                                                                         :version "v1alpha2"
                                                                                         :kind    "Orange"}}}
                                 {:route-name :CreateV1alpha1Carrot
                                  :summary "create a Carrot"
                                  :swagger-definition {:x-kubernetes-action             "create"
                                                       :x-kubernetes-group-version-kind {:group   "vegetables.group.dev"
                                                                                         :version "v1alpha1"
                                                                                         :kind    "Carrot"}}}
                                 {:route-name :UpdateV1alpha1Carrot
                                  :summary "replace the specified Carrot"
                                  :swagger-definition {:x-kubernetes-action             "update"
                                                       :x-kubernetes-group-version-kind {:group   "vegetables.group.dev"
                                                                                         :version "v1alpha1"
                                                                                         :kind    "Carrot"}}}]}]
      (is (match? (k8s/explore k8s-client)
                  [[:Carrot
                    [:create "create a Carrot"]
                    [:update "replace the specified Carrot"]]
                   [:Orange
                    [:create "create an Orange"]
                    [:update "replace the specified Orange"]]]))
      (is (match? (k8s/explore k8s-client :Carrot)
                  [:Carrot
                   [:create "create a Carrot"]
                   [:update "replace the specified Carrot"]]))
      (is (match? (k8s/explore k8s-client :Orange)
                  [:Orange
                   [:create "create an Orange"]
                   [:update "replace the specified Orange"]]))))
  (testing "kind not in preffered version"
    (let [k8s-client {::k8s/core-api-versions {:versions ["v1"]}
                      ::k8s/api-group-list {:groups [{:name "fruits.group.dev"
                                                      :versions [{:groupVersion "fruits.group.dev/v1alpha2" :version "v1beta1"}
                                                                 {:groupVersion "fruits.group.dev/v1alpha1" :version "v1alpha1"}]
                                                      :preferredVersion {:groupVersion "fruits.group.dev/v1beta1" :version "v1alpha1"}}]}
                      :handlers [{:route-name         :CreateV1beta1Orange
                                  :summary "create an Orange"
                                  :swagger-definition {:x-kubernetes-action             "create"
                                                       :x-kubernetes-group-version-kind {:group   "fruits.group.dev"
                                                                                         :version "v1beta1"
                                                                                         :kind    "Orange"}}}
                                 {:route-name         :UpdateV1beta1Orange
                                  :summary "replace the specified Orange"
                                  :swagger-definition {:x-kubernetes-action             "update"
                                                       :x-kubernetes-group-version-kind {:group   "fruits.group.dev"
                                                                                         :version "v1beta1"
                                                                                         :kind    "Orange"}}}
                                 {:route-name :CreateV1alpha1Apple
                                  :summary "create an Apple"
                                  :swagger-definition {:x-kubernetes-action             "create"
                                                       :x-kubernetes-group-version-kind {:group   "fruits.group.dev"
                                                                                         :version "v1alpha1"
                                                                                         :kind    "Apple"}}}
                                 {:route-name :UpdateV1alpha1Apple
                                  :summary "replace the specified Apple"
                                  :swagger-definition {:x-kubernetes-action             "update"
                                                       :x-kubernetes-group-version-kind {:group   "fruits.group.dev"
                                                                                         :version "v1alpha1"
                                                                                         :kind    "Apple"}}}]}]
      (is (match? (k8s/explore k8s-client)
                  [[:Apple
                    [:create "create an Apple"]
                    [:update "replace the specified Apple"]]
                   [:Orange
                    [:create "create an Orange"]
                    [:update "replace the specified Orange"]]]))
      (is (match? (k8s/explore k8s-client :Apple)
                  [:Apple
                   [:create "create an Apple"]
                   [:update "replace the specified Apple"]]))
      (is (match? (k8s/explore k8s-client :Orange)
                  [:Orange
                   [:create "create an Orange"]
                   [:update "replace the specified Orange"]])))))
