(ns kubernetes-api.interceptors.auth-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.interceptors.auth :as interceptors.auth]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test]
            [mockfn.core :refer [providing]]))

(deftest auth-test
  (testing "request with basic-auth"
    (is (match? {:request {:basic-auth "floki:freya1234"}}
                ((:enter (interceptors.auth/new {:username "floki"
                                                 :password "freya1234"})) {}))))
  (testing "request with client certificate"
    (providing [(#'interceptors.auth/new-ssl-engine {:client-cert "/some/client.crt"
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
                ((:enter (interceptors.auth/new {:token-fn (constantly "TOKEN")})) {})))))
