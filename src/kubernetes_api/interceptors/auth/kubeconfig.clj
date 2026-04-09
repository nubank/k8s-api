(ns kubernetes-api.interceptors.auth.kubeconfig
  (:require
    [cheshire.core :as json]
    [kubernetes-api.interceptors.auth.options :as auth.options]
    [clojure.java.shell :as sh]
    [tripod.log :as log]
    [clojure.string :as string]))

(defn env-var-map
  [env]
  (into {} (map (juxt :name :value) env)))

(defn parse-output [{:keys [out exit err] :as output}]
  (if (zero? exit)
    {:exec-credential (json/parse-string out true) :err err}
    output))

(defn new-exec-credential [{:keys [apiVersion provideClusterInfo] :as _user-exec} cluster]
  {:apiVersion (or apiVersion "client.authentication.k8s.io/v1")
   :kind "ExecCredential"
   :spec (merge (when provideClusterInfo {:cluster cluster})
                {:interactive false})})

(defn exec-fn [{:keys [user cluster] :as _context-info}]
  (let [{:keys [command args env] :as user-exec} (:exec user)]
    (fn [& _]
      (log/info :msg "executing exec command" :command command :args args :env env)
      (parse-output (apply sh/sh (cons command (concat args [:env (merge (env-var-map env)
                                                                         {"KUBERNETES_EXEC_INFO" (json/generate-string (new-exec-credential user-exec cluster))})])))))))




(defprotocol CredentialGetter
  (get-credential [this]))

(defn expired? [{:keys [status] :as _exec-credential}]
  (if-let [expiration-timestamp (:expirationTimestamp status)]
    (.isAfter (java.time.Instant/now)
              (java.time.Instant/parse expiration-timestamp))
    true))

(defn string->base64 [s]
  (.encodeToString (java.util.Base64/getMimeEncoder) 
                   (.getBytes s java.nio.charset.StandardCharsets/UTF_8)))

(defn exec-credential->auth-options [{:keys [status] :as _exec-credential} cluster]
  (cond (some? (:token status))
        {:token (:token status)}

        (and (some? (:clientCertificateData status))
             (some? (:clientKeyData status))
             (some? (:certificate-authority-data cluster)))
        {:client-certificate-data (string->base64 (:clientCertificateData status))
         :client-key-data (string->base64 (:clientKeyData status))
         :certificate-authority-data (:certificate-authority-data cluster)}))

(defn credentials-fn [exec-runner]
  (fn [& _] (get-credential exec-runner)))

(defrecord ExecCredentialRunner [exec-credential exec-fn cluster]
  CredentialGetter
  (get-credential [_]
    (let [exec-credential* @exec-credential]
      (exec-credential->auth-options
       (if (and (not (expired? exec-credential*))
                (some? exec-credential*))
         exec-credential*
         (swap! exec-credential (fn [_] (:exec-credential (exec-fn)))))
       cluster))))

(defn new-exec-runner [{:keys [cluster] :as context-info}]
  (map->ExecCredentialRunner {:exec-credential (atom nil)
                              :exec-fn (exec-fn context-info)
                              :cluster cluster}))

(defn auth-options 
  "Returns a map of authentication options based on the context info.
   This options should be used to instantiate a kubernetes-api.core/client."
  [{:keys [user cluster] :as context-info}]
  (cond 
    (and (auth.options/client-cert-key-pair? user)
         (auth.options/ca-cert? cluster)) 
    (select-keys (merge user cluster) [:client-cert :client-certificate-data :client-key :client-key-data :ca-cert :certificate-authority-data])

    (auth.options/token? user)
    (select-keys user [:token])

    (auth.options/token-file? {:token-file (:tokenFile user)})
    {:token-fn (fn [] (string/trim-newline (slurp (:tokenFile user))))}

    (auth.options/basic-auth? user)
    (select-keys user [:username :password])

    (auth.options/exec? user)
    {:auth-fn (credentials-fn (new-exec-runner context-info))}

    :else {}))
