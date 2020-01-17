(ns kubernetes-api.interceptors.auth
  (:require [less.awful.ssl :as ssl]
            [tripod.log :as log]))

(defn- new-ssl-engine
  [{:keys [ca-cert client-cert client-key]}]
  (ssl/ssl-context->engine (ssl/ssl-context client-key client-cert ca-cert)))

(defn- client-certs? [{:keys [ca-cert client-cert client-key]}]
  (every? some? [ca-cert client-cert client-key]))

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
   (cond
     (basic-auth? opts) {:basic-auth (basic-auth opts)}
     (token? opts) {:oauth-token (:token opts)}
     (token-fn? opts) {:oauth-token (token-fn opts)}
     (client-certs? opts) {:sslengine (new-ssl-engine opts)}
     :else (do (log/info "No authentication method found")
               {}))))

(defn new [opts]
  {:name  ::authentication
   :enter (fn [context]
            (update context :request #(merge % (request-auth-params opts))))})
