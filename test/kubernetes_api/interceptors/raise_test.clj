(ns kubernetes-api.interceptors.raise-test
  (:require [clojure.test :refer [deftest is testing]]
            [kubernetes-api.interceptors.raise :as interceptors.raise]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [tripod.context :as tc]))

(defn- run-interceptor [interceptor input]
  (tc/execute (tc/enqueue input interceptor)))

(deftest raise-test
  (let [raise-interceptor (interceptors.raise/new {})]
    (testing "should raise the body to be the response on 2xx status"
      (is (match? {:response {:status 200
                              :body {:my :body}}}
                  (run-interceptor raise-interceptor {:response {:status 200 :body {:my :body}}}))))

    (testing "should have the request/response on metadata"
      (is (match? {:request  {:my :request}
                   :response {:status 200
                              :body   {:my :body}}}
                  (meta
                   (run-interceptor raise-interceptor {:request  {:my :request}
                                                       :response {:status 200
                                                                  :body   {:my :body}}})))))

    (testing "return an exception on 4XX responses"
      (is (match? (m/via (comp ex-data :kubernetes-api.core/error :response)
                         {:type     :bad-request,
                          :response {:status 400}})
                  (run-interceptor raise-interceptor {:response {:status 400}}))))))
