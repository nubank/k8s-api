(ns kubernetes-api.interceptors.auth.options)

(defn ca-cert? [{:keys [ca-cert certificate-authority certificate-authority-data]}]
  (or (some? ca-cert)
      (some? certificate-authority)
      (some? certificate-authority-data)))

(defn client-cert? [{:keys [client-cert client-certificate-data]}]
  (or (some? client-cert)
      (some? client-certificate-data)))

(defn client-key? [{:keys [client-key client-key-data]}]
  (or (some? client-key)
      (some? client-key-data)))

(defn client-cert-key-pair? [opts]
  (and (client-cert? opts) (client-key? opts)))

(defn mtls-certs? [opts]
  (and (ca-cert? opts) (client-cert? opts) (client-key? opts)))

(defn basic-auth? [{:keys [username password]}]
  (every? some? [username password]))

(defn token? [{:keys [token]}]
  (some? token))

(defn token-fn? [{:keys [token-fn]}]
  (some? token-fn))

(defn token-file? [{:keys [token-file]}]
  (some? token-file))

(defn exec? [{:keys [exec]}]
  (some? exec))

(defn auth-fn? [{:keys [auth-fn]}]
  (some? auth-fn))
