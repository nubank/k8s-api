(ns kubernetes-api.interceptors.raise-test
  (:require [clojure.test :refer [deftest testing is]]
            [kubernetes-api.interceptors.raise :as interceptors.raise]
            [matcher-combinators.test :refer [match? thrown-match?]]))

(deftest raise-test
  (let [{:keys [leave]} interceptors.raise/interceptor]
    (testing "should raise the body to be the response on 2xx status"
      (is (match?
           {:response {:my :body}}
           (leave {:response {:status 200
                              :body   {:my :body}}}))))
    (testing "should have the request/response on metadata"
      (is (match?
           {:request  {:my :request}
            :response {:status 200
                       :body   {:my :body}}}
           (meta (leave {:request  {:my :request}
                         :response {:status 200
                                    :body   {:my :body}}})))))

    (testing "should raise exception on 4XX responses"
      (is (thrown-match?
           {:type :unauthorized}
           (leave {:response {:status 401}}))))))
