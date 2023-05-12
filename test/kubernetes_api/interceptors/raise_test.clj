(ns kubernetes-api.interceptors.raise-test
  (:require [clojure.test :refer [deftest is testing]]
            [kubernetes-api.interceptors.raise :as interceptors.raise]
            [matcher-combinators.test :refer [match? thrown-match?]]
            [tripod.context :as tc]))

(defn- run-interceptor [interceptor input]
  (tc/execute 
   (tc/enqueue input interceptor)))

(deftest raise-test
  (let [raise-interceptor (interceptors.raise/new {})]
    (testing "should raise the body to be the response on 2xx status"
      (is (match? {:response {:my :body}}
                  (run-interceptor raise-interceptor {:response {:status 200 :body {:my :body}}}))))
    
    (testing "should have the request/response on metadata"
      (is (match? {:request  {:my :request}
                   :response {:status 200
                              :body   {:my :body}}}
                  (meta
                   (run-interceptor raise-interceptor {:request  {:my :request}
                                                       :response {:status 200
                                                                  :body   {:my :body}}})))))

    (testing "should raise exception on 4XX responses"
      (is (thrown-match? {:type :unauthorized}
                         (run-interceptor raise-interceptor {:response {:status 401}}))))))
