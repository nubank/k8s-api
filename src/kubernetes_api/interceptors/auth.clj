(ns kubernetes-api.interceptors.auth
  (:require [kubernetes-api.interceptors.auth.ssl :as auth.ssl]
            [tripod.log :as log]))

(defn- ca-cert? [{:keys [ca-cert certificate-authority-data]}]
  (or (some? ca-cert)
      (some? certificate-authority-data)))

(defn- client-cert? [{:keys [client-cert client-certificate-data]}]
  (or (some? client-cert)
      (some? client-certificate-data)))

(defn- client-key? [{:keys [client-key client-key-data]}]
  (or (some? client-key)
      (some? client-key-data)))

(defn- client-certs? [opts]
  (and (ca-cert? opts) (client-cert? opts) (client-key? opts)))

(defn- basic-auth? [{:keys [username password]}]
  (every? some? [username password]))

(defn- basic-auth [{:keys [username password]}]
  (str username ":" password))

(defn- token? [{:keys [token]}]
  (some? token))

(defn- token-fn? [{:keys [token-fn]}]
  (some? token-fn))

(defn request-auth-params [{:keys [token-fn insecure?] :as opts}]
  (merge
    {:insecure? (or insecure? false)}
   (when (and (ca-cert? opts) (not (client-certs? opts)))
     {:sslengine (auth.ssl/ca-cert->ssl-engine opts)})
   (cond
     (basic-auth? opts) {:basic-auth (basic-auth opts)}
     (token? opts) {:oauth-token (:token opts)}
     (token-fn? opts) {:oauth-token (token-fn opts)}
     (client-certs? opts) {:sslengine (auth.ssl/client-certs->ssl-engine opts)}
     :else (do (log/info "No authentication method found")
               {}))))

(defn new [opts]
  {:name  ::authentication
   :enter (fn [context]
            (update context :request #(merge % (request-auth-params opts))))})
