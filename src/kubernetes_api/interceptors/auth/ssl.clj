(ns kubernetes-api.interceptors.auth.ssl
  (:require [clojure.string :as str]
            [less.awful.ssl :as ssl])
  (:import (java.io ByteArrayInputStream)
           (java.nio.charset StandardCharsets)
           (java.security KeyStore)
           (java.security.spec PKCS8EncodedKeySpec)
           (java.security.cert Certificate)
           (javax.net.ssl SSLContext
                          TrustManager
                          KeyManager)))

(defn load-certificate ^Certificate [{:keys [cert-file cert-data]}]
  (cond
    (some? cert-file) (ssl/load-certificate cert-file)
    (some? cert-data) (with-open [stream (ByteArrayInputStream. (ssl/base64->binary cert-data))]
                        (.generateCertificate ssl/x509-cert-factory stream))))

(defn trust-store [cert]
  (doto (KeyStore/getInstance "JKS")
    (.load nil nil)
    (.setCertificateEntry "cacert" (load-certificate cert))))

(defn- der-length ^bytes [n]
  (if (< n 0x80)
    (byte-array [n])
    (let [octets (loop [n n acc []]
                   (if (zero? n)
                     acc
                     (recur (unsigned-bit-shift-right n 8) (cons (bit-and n 0xff) acc))))]
      (byte-array (cons (bit-or 0x80 (count octets)) octets)))))

(defn- der-tlv ^bytes [tag ^bytes value]
  (byte-array (concat [(int tag)] (der-length (alength value)) value)))

(defn- pkcs1->pkcs8 ^bytes [^bytes pkcs1-der]
  ;; Wrap a PKCS#1 RSA key in a PKCS#8 AlgorithmIdentifier envelope so that
  ;; PKCS8EncodedKeySpec can parse it.
  ;; RSA OID: 1.2.840.113549.1.1.1
  (let [rsa-oid  (byte-array [0x06 0x09 0x2a 0x86 0x48 0x86 0xf7 0x0d 0x01 0x01 0x01])
        null-val (byte-array [0x05 0x00])
        alg-id   (der-tlv 0x30 (byte-array (concat rsa-oid null-val)))
        version  (byte-array [0x02 0x01 0x00])
        key-os   (der-tlv 0x04 pkcs1-der)]
    (der-tlv 0x30 (byte-array (concat version alg-id key-os)))))

(defn base64->private-key
  [base64-private-key]
  (let [pem-str   (String. (ssl/base64->binary base64-private-key) StandardCharsets/UTF_8)
        pkcs1?    (str/includes? pem-str "BEGIN RSA PRIVATE KEY")
        der-bytes (->> pem-str
                       (re-find #"(?ms)^-----BEGIN ?.*? PRIVATE KEY-----$(.+)^-----END ?.*? PRIVATE KEY-----$")
                       last
                       ssl/base64->binary)]
    (->> (if pkcs1? (pkcs1->pkcs8 der-bytes) der-bytes)
         PKCS8EncodedKeySpec.
         (.generatePrivate ssl/rsa-key-factory))))

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
