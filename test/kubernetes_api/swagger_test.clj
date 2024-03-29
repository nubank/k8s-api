(ns kubernetes-api.swagger-test
  (:require [clojure.test :refer :all]
            [matcher-combinators.test :refer [match?]]
            [kubernetes-api.swagger :as swagger]))

(deftest remove-watch-endpoints-test
  (is (= {:paths {"/foo/bar" 'irrelevant
                  "/foo/baz" 'irrelevant}}
         (swagger/remove-watch-endpoints
          {:paths {"/foo/bar"       'irrelevant
                   "/foo/baz"       'irrelevant
                   "/foo/watch/bar" 'irrelevant}}))))

(deftest fix-k8s-verbs-test
  (testing "replace post for create"
    (is (= {:paths {"/foo/bar" {:get {:x-kubernetes-action "create"}}}}
           (swagger/fix-k8s-verb
            {:paths {"/foo/bar" {:get {:x-kubernetes-action "post"}}}}))))
  (testing "replace watchlist for watch"
    (is (= {:paths {"/foo/bar" {:get {:x-kubernetes-action "watch"}}}}
           (swagger/fix-k8s-verb
            {:paths {"/foo/bar" {:get {:x-kubernetes-action "watchlist"}}}}))))
  (testing "replace put for update"
    (is (= {:paths {"/foo/bar" {:get {:x-kubernetes-action "update"}}}}
           (swagger/fix-k8s-verb
            {:paths {"/foo/bar" {:get {:x-kubernetes-action "put"}}}}))))
  (testing "leave unaltered the rest"
    (doseq [verb ["create" "get" "list" "watch" "update" "patch" "delete" "deletecollection"]]
      (testing "replace put for update"
        (is (= {:paths {"/foo/bar" {:get {:x-kubernetes-action verb}}}}
               (swagger/fix-k8s-verb
                {:paths {"/foo/bar" {:get {:x-kubernetes-action verb}}}})))))))

(deftest fix-consumes-test
  (testing "fixes consumes content types when its */*"
    (is (= {:paths {"/foo/bar" {:get {:consumes ["application/json"]}}}}
           (swagger/fix-consumes
            {:paths {"/foo/bar" {:get {:consumes ["*/*"]}}}}))))
  (testing "leave unaltered when its not */*"
    (is (= {:paths {"/foo/bar" {:get {:consumes ["application/yaml"]}}}}
           (swagger/fix-consumes
            {:paths {"/foo/bar" {:get {:consumes ["application/yaml"]}}}})))))

(deftest add-patch-routes-test
  (testing "for each patch operation, add all patch strategies"
    (is (match? {:paths {"/foo/bar" {:patch/json {:parameters [{:name "body"
                                                                :in "body"
                                                                :schema swagger/rfc6902-json-schema}],
                                                  :operationId "PatchCoreV1ResourceJsonPatch"
                                                  :consumes ["application/json-patch+json"]
                                                  :x-kubernetes-action "patch/json"}
                                     :patch/json-merge {:parameters [{:name "body"
                                                                      :in "body"
                                                                      :schema 'schema}]
                                                        :operationId "PatchCoreV1ResourceJsonMerge"
                                                        :consumes ["application/merge-patch+json"]
                                                        :x-kubernetes-action "patch/json-merge"}
                                     :patch/strategic {:parameters [{:name "body"
                                                                     :in "body"
                                                                     :schema 'schema}]
                                                       :operationId "PatchCoreV1ResourceStrategicMerge"
                                                       :consumes ["application/strategic-merge-patch+json"]
                                                       :x-kubernetes-action "patch/strategic"}
                                     :apply/server {:parameters [{:name "body"
                                                                  :in "body"
                                                                  :schema 'schema}]
                                                    :operationId "PatchCoreV1ResourceApplyServerSide"
                                                    :consumes ["application/apply-patch+yaml"]
                                                    :x-kubernetes-action "apply/server"}}}}
                (swagger/add-patch-routes
                  {:paths {"/foo/bar" {:put {:parameters [{:name "body"
                                                            :in "body"
                                                            :schema 'schema}]
                                             :x-kubernetes-action "update"}
                                       :patch {:operationId "PatchCoreV1Resource"
                                              :parameters [{:name "body"
                                                            :in "body"
                                                            :schema 'broken}]}}}})))))

(deftest add-summary-test
  (testing "copies description to summary if summary doesnt exists"
    (is (= {:paths {"/foo/bar" {:get {:description "foo"
                                      :summary "foo"}}}}
           (swagger/add-summary
            {:paths {"/foo/bar" {:get {:description "foo"}}}})))
    (is (= {:paths {"/foo/bar" {:get {:summary "foo" :description "foo bar baz"}}}}
           (swagger/add-summary
            {:paths {"/foo/bar" {:get {:summary "foo"
                                       :description "foo bar baz"}}}})))))

(deftest add-some-routes-test
  (is (= {:definitions {:Book  {:type "object", :properties {:name {:type "string"}}},
                        :Movie {:type "object", :properties {:name {:type "string"}}}},
          :paths       {"/books/{id}"  {:get {:response-schemas {"200" {:$ref "#/definitions/Book"}}}},
                        "/movies/{id}" {:get {:response-schemas {"200" {:$ref "#/definitions/Movie"}}}}}}
         (swagger/add-some-routes {:definitions {:Book {:type       "object"
                                                        :properties {:name {:type "string"}}}}
                                   :paths       {"/books/{id}" {:get {:response-schemas {"200" {:$ref "#/definitions/Book"}}}}}}
                                  {:Movie {:type       "object"
                                           :properties {:name {:type "string"}}}}
                                  {"/movies/{id}" {:get {:response-schemas {"200" {:$ref "#/definitions/Movie"}}}}}))))
