(ns kubernetes-api.internals.martian
  (:require [martian.core :as martian]))

(defn response-for
  "Workaround to throw exceptions in the client like connection timeout"
  [& args]
  (let [{:kubernetes-api.core/keys [error] :as response} (deref (apply martian/response-for args))]
    (if (instance? Throwable error)
      (throw error)
      response)))
