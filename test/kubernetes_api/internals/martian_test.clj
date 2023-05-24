(ns kubernetes-api.internals.martian-test
  (:require [clojure.test :refer [deftest is testing]]
            [kubernetes-api.internals.martian :as internals.martian]
            [martian.core :as martian]
            [matcher-combinators.test :refer [thrown-match?]]
            [mockfn.macros :refer [providing]])
  (:import [clojure.lang ExceptionInfo]))

(deftest response-for
  (testing "Throws exception from client"
    (providing [(martian/response-for 'martian 'testing)
                (delay {:kubernetes-api.core/error (ex-info "Test error" {:type :error})})]
      (is (thrown-match? ExceptionInfo
                         {:type :error}
                         (internals.martian/response-for 'martian 'testing))))))