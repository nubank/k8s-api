(ns kubernetes-api.internals.martian
  (:require [martian.core :as martian]))

(defn response-for
  "Workaround to throw exceptions in the client like connection timeout"
  [& args]
  (let [response (deref (apply martian/response-for args))]
    (if (instance? Throwable (:error response))
      (throw (:error response))
      response)))
