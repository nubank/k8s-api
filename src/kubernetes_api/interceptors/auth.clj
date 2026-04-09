(ns kubernetes-api.interceptors.auth
  (:require [kubernetes-api.interceptors.auth.ssl :as auth.ssl]
            [kubernetes-api.interceptors.auth.options :as auth.options]
            [tripod.log :as log]))

(defn- basic-auth [{:keys [username password]}]
  (str username ":" password))

(defn request-auth-params [{:keys [token-fn auth-fn insecure?] :as opts}]
  (merge
   {:insecure? (or insecure? false)}
   (when (and (auth.options/ca-cert? opts) (not (auth.options/mtls-certs? opts)))
     {:sslengine (auth.ssl/ca-cert->ssl-engine opts)})
   (cond
     (auth.options/basic-auth? opts) {:basic-auth (basic-auth opts)}
     (auth.options/token? opts) {:oauth-token (:token opts)}
     (auth.options/token-fn? opts) {:oauth-token (token-fn opts)}
     (auth.options/mtls-certs? opts) {:sslengine (auth.ssl/client-certs->ssl-engine opts)}
     (auth.options/auth-fn? opts) (let [auth-opts (auth-fn)]
                                    (request-auth-params (dissoc auth-opts :auth-fn)))
     :else (do (log/info "No authentication method found")
               {}))))

(defn new [opts]
  {:name  ::authentication
   :enter (fn [context]
            (update context :request #(merge % (request-auth-params opts))))})
