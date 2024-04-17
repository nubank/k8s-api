(ns kubernetes-api.swagger-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.core :as k8s]
            [kubernetes-api.swagger :as swagger]
            [matcher-combinators.test :refer [match?]]))

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

(deftest group-version-test
  (testing "applies to string"
    (is (= {:group "apps" :version "v1"}
           (swagger/group-version "apps/v1")))
    (is (= {:group "apps"}
           (swagger/group-version "apps")))
    (is (= {:group "" :version "v1"}
           (swagger/group-version "core/v1"))))
  (testing "applies to keyword"
    (is (= {:group "apps" :version "v1"}
           (swagger/group-version :apps/v1)))
    (is (= {:group "apps"}
           (swagger/group-version :apps)))
    (is (= {:group "" :version "v1"}
           (swagger/group-version :core/v1)))))

(deftest from-group-version?-test
  (testing "applies to string"
    (is (swagger/from-group-version? "apps/v1" "/apis/apps/v1/namespaces/:namespace/deployments"))
    (is (swagger/from-group-version? "apps"  "/apis/apps/v1/namespaces/:namespace/deployments"))
    (is (swagger/from-group-version? "core/v1"  "/api/v1/namespaces/:namespace/pods")))
  (testing "applies to keyword"
    (is (swagger/from-group-version? :apps/v1 "/apis/apps/v1/namespaces/:namespace/deployments"))
    (is (swagger/from-group-version? :apps  "/apis/apps/v1/namespaces/:namespace/deployments"))
    (is (swagger/from-group-version? :core/v1  "/api/v1/namespaces/:namespace/pods"))))

(deftest from-apis-test
  (testing "returns the paths from the api version specified and the default paths"
    (is (=  {"/apis/"           {}
             "/api/"            {}
             "/apis/foo.bar/v1" {}}
            (swagger/from-apis ["foo.bar/v1"]
                               {"/apis/"           {}
                                "/api/"            {}
                                "/apis/foo/bar"    {}
                                "/apis/foo.bar/v1" {}})))
    (is (=  {"/apis/"           {}
             "/api/"            {}
             "/apis/foo/bar"    {}
             "/apis/foo.bar/v1" {}}
            (swagger/from-apis ["foo.bar/v1", "foo"]
                               {"/apis/"           {}
                                "/api/"            {}
                                "/apis/foo/bar"    {}
                                "/apis/foo.bar/v1" {}})))
    (is (= {"/apis/" {}
            "/api/"  {}}
           (swagger/from-apis ["foo.bar/v2"]
                              {"/apis/"           {}
                               "/api/"            {}
                               "/apis/foo/bar"    {}
                               "/apis/foo.bar/v1" {}})))))

(deftest filter-paths-test
  (testing "filter the paths for the api and version specified"
    (is (= {:paths {"/apis/"           {}
                    "/api/"            {}
                    "/apis/foo.bar/v1" {}}}
           (swagger/filter-paths {:paths {"/apis/"           {}
                                          "/api/"            {}
                                          "/apis/foo/bar"    {}
                                          "/apis/foo.bar/v1" {}}}
                                 ["foo.bar/v1"])))
    (is (= {:paths {"/apis/"           {}
                    "/api/"            {}
                    "/apis/foo/bar"    {}
                    "/apis/foo.bar/v1" {}}}
           (swagger/filter-paths {:paths {"/apis/"           {}
                                          "/api/"            {}
                                          "/apis/foo/bar"    {}
                                          "/apis/foo.bar/v1" {}}}
                                 ["foo.bar/v1" "foo"]))))

  (testing "do not update paths if api or version not found"
    (is (= {:paths {"/apis/"           {}
                    "/api/"            {}}}
           (swagger/filter-paths {:paths {"/apis/"           {}
                                          "/api/"            {}
                                          "/apis/foo/bar"    {}
                                          "/apis/foo.bar/v1" {}}}
                                 ["foo.bar/v2"]))))

  (testing "returns the default paths if api or version is missing"
    (is (= {:paths {"/apis/" {}
                    "/api/"  {}}}
           (swagger/filter-paths {:paths {"/apis/"           {}
                                          "/api/"            {}
                                          "/apis/foo/bar"    {}
                                          "/apis/foo.bar/v1" {}}} []))))

  (testing "Returns default apis"
    (is (match? {:paths {"/api/v1/configmaps"        {}
                         "/apis/apps/v1/deployments" {}
                         "/apis/apps/"               {}
                         "/apis/apps/v1/"            {}
                         "/logs/"                    {}
                         "/openid/v1/jwks/"          {}
                         "/version/"                 {}}}
         (-> (swagger/parse-swagger-file "test_swagger.json")
             (swagger/filter-paths k8s/default-apis))))))
