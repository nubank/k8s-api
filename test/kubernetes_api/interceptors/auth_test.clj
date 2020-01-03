(ns kubernetes-api.interceptors.auth-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.interceptors.auth :as interceptors.auth]))

(deftest auth-test
  (interceptors.auth/new {}))
