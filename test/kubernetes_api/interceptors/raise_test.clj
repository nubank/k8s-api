(ns kubernetes-api.interceptors.raise-test
  (:require [cheshire.core :as json]
            [clojure.string :as string]
            [clojure.test :refer [deftest is testing]]
            [kubernetes-api.interceptors.raise :as interceptors.raise]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]
            [tripod.context :as tc]))

(defn- run-interceptor [interceptor input]
  (tc/execute (tc/enqueue input interceptor)))

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

    (testing "return an exception on 4XX responses"
      (is (match? (m/via (comp ex-data :kubernetes-api.core/error :response)
                         {:type     :bad-request,
                          :response {:status 400}})
                  (run-interceptor raise-interceptor {:response {:status 400}}))))))

(defn- response-exception [response]
  (try
    (interceptors.raise/check-response response)
    (catch clojure.lang.ExceptionInfo error error)))

(deftest error-response-data-test
  (let [body {:kind "Status"
              :apiVersion "v1"
              :status "Failure"
              :reason "Forbidden"
              :message "ConfigMap create denied"
              :details {:kind "configmaps" :name "test-configmap"}
              :code 403}
        response {:status 403
                  :body body
                  :http-version "HTTP/1.1"
                  :diagnostics {:extension "preserved"}
                  :headers {:audit-id "test-audit-id"
                            :retry-after "1"
                            :content-type "application/json"
                            :server "test-apiserver"
                            :set-cookie "dummy-response-secret"}
                  :opts {:sslengine (.createSSLEngine (javax.net.ssl.SSLContext/getDefault))
                         :oauth-token "dummy-request-secret"
                         :headers {"Authorization" "Bearer dummy-request-secret"}}}
        expected {:type :forbidden
                  :response {:status 403
                             :body body
                             :http-version "HTTP/1.1"
                             :diagnostics {:extension "preserved"}
                             :headers {:audit-id "test-audit-id"
                                       :retry-after "1"
                                       :content-type "application/json"
                                       :server "test-apiserver"
                                       :set-cookie "redacted"}}}]
    (doseq [[path exception] [["discovery" (response-exception response)]
                              ["invoke" (get-in (run-interceptor (interceptors.raise/new {})
                                                                 {:response response})
                                                [:response :kubernetes-api.core/error])]]]
      (testing path
        (is (= "APIServer error: 403" (.getMessage exception)))
        (is (= expected (ex-data exception)))
        (is (nil? (.getCause exception)))
        (let [encoded (json/generate-string (Throwable->map exception))]
          (is (string? encoded))
          (is (not (.contains encoded "dummy-request-secret")))
          (is (not (.contains encoded "dummy-response-secret")))
          (is (not (.contains encoded "sslengine"))))))))

(deftest error-classification-test
  (doseq [[status type] [[404 :not-found] [409 :conflict] [503 :service-unavailable]]]
    (let [body {:details {:kind "namespaces" :name "test-namespace"}}
          exception (response-exception {:status status :body body})]
      (is (= {:type type :response {:status status :body body}}
             (ex-data exception)))))
  (testing "raw discovery error bodies remain available"
    (is (= {:type :bad-gateway :response {:status 502 :body "Bad Gateway"}}
           (ex-data (response-exception {:status 502 :body "Bad Gateway"})))))
  (testing "sensitive response headers are redacted even without diagnostic headers"
    (is (= {:type :unauthorized :response {:status 401 :headers {:set-cookie "redacted"}}}
           (ex-data (response-exception {:status 401
                                         :headers {:set-cookie "dummy-response-secret"}}))))))

(deftest error-response-headers-test
  (testing "sensitive header values are redacted without changing other fields"
    (doseq [header ["authorization" "proxy-authorization" "cookie" "set-cookie" "x-nu-svc-auth"]
            header [header (keyword header) (string/upper-case header) (keyword (string/upper-case header))]]
      (let [response {:status 401
                      :headers {header "dummy-response-secret"
                                :audit-id "test-audit-id"
                                :server "test-apiserver"}}
            exception (response-exception response)]
        (is (= {:type :unauthorized
                :response {:status 401
                           :headers {header "redacted"
                                     :audit-id "test-audit-id"
                                     :server "test-apiserver"}}}
               (ex-data exception)))
        (is (not (.contains (json/generate-string (Throwable->map exception)) "dummy-response-secret"))))))
  (testing "absent, nil, and empty headers remain unchanged"
    (doseq [response [{:status 401} {:status 401 :headers nil} {:status 401 :headers {}}]]
      (is (= {:type :unauthorized :response response}
             (ex-data (response-exception response)))))))

(deftest check-response-test
  (testing "successful responses still return their body"
    (is (= {:items []} (interceptors.raise/check-response {:status 200 :body {:items []}}))))
  (testing "transport failures retain their original exception"
    (let [failure (java.net.ConnectException. "Connection refused")]
      (is (identical? failure
                      (try
                        (interceptors.raise/check-response {:error failure})
                        (catch Exception error error)))))))
