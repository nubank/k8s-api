(ns kubernetes-api.interceptors.auth.ssl
  (:require [less.awful.ssl :as ssl])
  (:import (java.io ByteArrayInputStream)
           (java.security KeyStore)
           (java.security.spec PKCS8EncodedKeySpec)
           (java.security.cert Certificate)
           (javax.net.ssl SSLContext
                          TrustManager
                          KeyManager)))

(defn extract-cert-data
  [base64-cert-data]
  (let [extracted-data (->> base64-cert-data
                           (re-find #"(?ms)^-----BEGIN ?.*? CERTIFICATE-----$(.+)^-----END ?.*? CERTIFICATE-----$")
                           last)]
    (or extracted-data base64-cert-data)))

(defn base64->certificate
  [base64-cert-data]
  (let [data (extract-cert-data base64-cert-data)]
    (with-open [stream (ByteArrayInputStream. (ssl/base64->binary data))]
      (.generateCertificate ssl/x509-cert-factory stream))))

(defn load-certificate ^Certificate [{:keys [cert-file cert-data]}]
  (cond
    (some? cert-file) (ssl/load-certificate cert-file)
    (some? cert-data) (base64->certificate cert-data)))

(defn trust-store [cert]
  (doto (KeyStore/getInstance "JKS")
    (.load nil nil)
    (.setCertificateEntry "cacert" (load-certificate cert))))

(defn base64->private-key
  [base64-private-key]
(->> (String. (ssl/base64->binary base64-private-key) java.nio.charset.StandardCharsets/UTF_8)
    (re-find #"(?ms)^-----BEGIN ?.*? PRIVATE KEY-----$(.+)^-----END ?.*? PRIVATE KEY-----$")
    last
    ssl/base64->binary
    PKCS8EncodedKeySpec.
    (.generatePrivate ssl/rsa-key-factory)))

(defn private-key [{:keys [key key-data]}]
  (cond
    (some? key)      (ssl/private-key key)
    (some? key-data) (base64->private-key key-data)))

(defn ^"[Ljava.security.cert.Certificate;" load-certificate-chain
  [{:keys [cert-file cert-data]}]
  (cond
    (some? cert-file) (ssl/load-certificate-chain cert-file)
    (some? cert-data) (with-open [stream (ByteArrayInputStream. (ssl/base64->binary cert-data))]
                        (let [^"[Ljava.security.cert.Certificate;" ar (make-array Certificate 0)]
                          (.toArray (.generateCertificates ssl/x509-cert-factory stream) ar)))))

(defn key-store
  [key cert]
  (let [pk     (private-key key)
        certs   (load-certificate-chain cert)]
    (doto (KeyStore/getInstance (KeyStore/getDefaultType))
      (.load nil nil)
      ; alias, private key, password, certificate chain
      (.setKeyEntry "cert" pk ssl/key-store-password certs))))

(defn client-certs->ssl-context ^SSLContext
  [client-key client-cert ca-cert]
   (let [key-manager (ssl/key-manager (key-store client-key client-cert))
         trust-manager (ssl/trust-manager (trust-store ca-cert))]
     (doto (SSLContext/getInstance "TLSv1.2")
       (.init (into-array KeyManager [key-manager])
              (into-array TrustManager [trust-manager])
              nil))))

(defn client-certs->ssl-engine
  [{:keys [ca-cert certificate-authority-data client-cert client-certificate-data client-key client-key-data]}]
  (let [key {:key client-key
             :key-data client-key-data}
        cert {:cert-file client-cert
              :cert-data client-certificate-data}
        ca-crt {:cert-file ca-cert
                :cert-data certificate-authority-data}]
    (ssl/ssl-context->engine
     (client-certs->ssl-context key cert ca-crt))))

(defn ca-cert->ssl-context [cert]
  (doto (SSLContext/getInstance "TLSv1.2")
       (.init nil (into-array TrustManager [(less.awful.ssl/trust-manager (trust-store cert))]) nil)))

(defn ca-cert->ssl-engine
  [{:keys [ca-cert certificate-authority-data]}]
  (ssl/ssl-context->engine
   (ca-cert->ssl-context {:cert-file ca-cert
                          :cert-data certificate-authority-data})))
