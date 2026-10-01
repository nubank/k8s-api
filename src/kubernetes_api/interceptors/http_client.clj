(ns kubernetes-api.interceptors.http-client
  (:require [org.httpkit.client :as http]
            [org.httpkit.sni-client :as sni-client]))

(defn new
  "Builds an interceptor that attaches a dedicated http-kit HttpClient to every
   request made by this client instance.

   Without this, martian-httpkit's perform-request calls org.httpkit.client/request
   without a :client option, so every kubernetes-api client in the JVM falls back to
   http-kit's shared default-client and its single keep-alive connection pool, keyed
   only by addr+host. When multiple clusters are reachable through the same host:port
   (e.g. Teleport, routed by client certificate), a connection authenticated for one
   cluster can be silently reused for another."
  [_opts]
  (let [http-client (http/make-client {:ssl-configurer sni-client/ssl-configurer})]
    {:name  ::http-client
     :enter (fn [ctx] (update ctx :request assoc :client http-client))}))
