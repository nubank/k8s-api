(ns kubernetes-api.interceptors.http-client-test
  (:require [clojure.test :refer [deftest is testing]]
            [kubernetes-api.interceptors.http-client :as interceptors.http-client]
            [matcher-combinators.test :refer [match?]]
            [tripod.context :as tc]))

(defn- run-interceptor [interceptor input]
  (tc/execute (tc/enqueue input interceptor)))

(deftest http-client-test
  (testing "attaches an http-kit HttpClient to the request"
    (let [interceptor (interceptors.http-client/new {})
          result      (run-interceptor interceptor {:request {:url "https://example.com"}})]
      (is (match? {:request {:url "https://example.com"}}
                  result))
      (is (instance? org.httpkit.client.HttpClient (get-in result [:request :client])))))

  (testing "different client instances get distinct HttpClients"
    (let [client-1 (get-in (run-interceptor (interceptors.http-client/new {}) {:request {}})
                           [:request :client])
          client-2 (get-in (run-interceptor (interceptors.http-client/new {}) {:request {}})
                           [:request :client])]
      (is (not (identical? client-1 client-2))))))
