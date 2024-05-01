(ns kubernetes-api.interceptors.auth-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.interceptors.auth :as interceptors.auth]
            [kubernetes-api.interceptors.auth.ssl :as auth.ssl]
            [matcher-combinators.standalone :refer [match?]]
            [matcher-combinators.test]
            [mockfn.macros]
            [clojure.string :as string]))

(deftest auth-test
  (testing "request with basic-auth"
    (is (match? {:request {:basic-auth "floki:freya1234"}}
                ((:enter (interceptors.auth/new {:username "floki"
                                                 :password "freya1234"})) {}))))
  (testing "request with client certificate"
    (mockfn.macros/providing
     [(auth.ssl/client-certs->ssl-engine {:client-cert "/some/client.crt"
                                          :client-key  "/some/client.key"
                                          :ca-cert     "/some/ca-cert.crt"}) 'SSLEngine]
     (is (match? {:request {:sslengine #(= 'SSLEngine %)}}
                 ((:enter (interceptors.auth/new {:client-cert "/some/client.crt"
                                                  :client-key  "/some/client.key"
                                                  :ca-cert     "/some/ca-cert.crt"})) {})))))
  (testing "request with token"
    (is (match? {:request {:oauth-token "TOKEN"}}
                ((:enter (interceptors.auth/new {:token "TOKEN"})) {}))))
  (testing "request with token-fn"
    (is (match? {:request {:oauth-token "TOKEN"}}
                ((:enter (interceptors.auth/new {:token-fn (constantly "TOKEN")})) {}))))
  (testing "request with token and ca-certificate"
    (mockfn.macros/providing
     [(auth.ssl/ca-cert->ssl-engine (match? {:ca-cert "/some/ca-cert.crt"})) 'SSLEngine]
     (is (match? {:request {:sslengine #(= 'SSLEngine %)
                            :oauth-token "TOKEN"}}
                 ((:enter (interceptors.auth/new {:token   "TOKEN"
                                                  :ca-cert "/some/ca-cert.crt"})) {}))))))

(deftest extract-cert-data-test
  (testing "should extract data between header and footer"
    (is (= "\nY2VydGlmaWNhdGUtYXV0aG9yaXR5LWRhdGEK\n"
           (auth.ssl/extract-cert-data "-----BEGIN CERTIFICATE-----\nY2VydGlmaWNhdGUtYXV0aG9yaXR5LWRhdGEK\n-----END CERTIFICATE-----"))))

  (testing "should keep same data if no header and footer"
    (is (= "Y2VydGlmaWNhdGUtYXV0aG9yaXR5LWRhdGEK"
           (auth.ssl/extract-cert-data "Y2VydGlmaWNhdGUtYXV0aG9yaXR5LWRhdGEK"))))

  (testing "no op if empty string"
    (is (string/blank? (auth.ssl/extract-cert-data "")))))
