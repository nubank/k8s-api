(ns kubernetes-api.interceptors.auth.ssl-test
  (:require [clojure.test :refer :all]
            [kubernetes-api.interceptors.auth.ssl :as ssl])
  (:import (java.io StringWriter)
           (java.security KeyPairGenerator Security PrivateKey)
           (java.util Base64)
           (org.bouncycastle.asn1.pkcs PrivateKeyInfo)
           (org.bouncycastle.jce.provider BouncyCastleProvider)
           (org.bouncycastle.openssl.jcajce JcaPEMWriter)
           (org.bouncycastle.util.io.pem PemObject PemWriter)))

;; Register BC provider so KeyPairGenerator produces BC-native key types,
;; which JcaPEMWriter can fully serialize (including EC public key in SEC1).
(Security/addProvider (BouncyCastleProvider.))

(defn- generate-private-key [algorithm]
  (let [gen (KeyPairGenerator/getInstance algorithm "BC")]
    (case algorithm
      "RSA" (.initialize gen 2048)
      "EC"  (.initialize gen (java.security.spec.ECGenParameterSpec. "secp256r1")))
    (-> gen .generateKeyPair .getPrivate)))

(defn- private-key->pkcs8-pem
  "Serializes to PKCS#8 PEM (BEGIN PRIVATE KEY) by writing the PrivateKeyInfo directly."
  [^PrivateKey k]
  (let [pki (PrivateKeyInfo/getInstance (.getEncoded k))
        sw  (StringWriter.)]
    (with-open [w (JcaPEMWriter. sw)]
      (.writeObject w pki))
    (.toString sw)))

(defn- private-key->pkcs1-pem
  "Serializes to PKCS#1 PEM using JcaPEMWriter on the raw key (RSA → BEGIN RSA PRIVATE KEY,
  EC → BEGIN EC PRIVATE KEY)."
  [^PrivateKey k]
  (let [sw (StringWriter.)]
    (with-open [w (JcaPEMWriter. sw)]
      (.writeObject w k))
    (.toString sw)))

(defn- pem->base64 [^String pem]
  (.encodeToString (Base64/getEncoder) (.getBytes pem "UTF-8")))

(deftest base64->private-key-test
  (testing "PKCS#8 RSA private key (BEGIN PRIVATE KEY)"
    (let [k   (generate-private-key "RSA")
          b64 (pem->base64 (private-key->pkcs8-pem k))]
      (is (instance? PrivateKey (ssl/base64->private-key b64)))
      (is (= "RSA" (.getAlgorithm ^PrivateKey (ssl/base64->private-key b64))))))

  (testing "PKCS#1 RSA private key (BEGIN RSA PRIVATE KEY)"
    (let [k   (generate-private-key "RSA")
          b64 (pem->base64 (private-key->pkcs1-pem k))]
      (is (instance? PrivateKey (ssl/base64->private-key b64)))
      (is (= "RSA" (.getAlgorithm ^PrivateKey (ssl/base64->private-key b64))))))

  (testing "PKCS#8 EC private key (BEGIN PRIVATE KEY)"
    (let [k   (generate-private-key "EC")
          b64 (pem->base64 (private-key->pkcs8-pem k))
          pk  (ssl/base64->private-key b64)]
      (is (instance? PrivateKey pk))
      (is (contains? #{"EC" "ECDSA"} (.getAlgorithm ^PrivateKey pk)))))

  (testing "PKCS#1 EC private key (BEGIN EC PRIVATE KEY)"
    (let [k   (generate-private-key "EC")
          b64 (pem->base64 (private-key->pkcs1-pem k))
          pk  (ssl/base64->private-key b64)]
      (is (instance? PrivateKey pk))
      (is (contains? #{"EC" "ECDSA"} (.getAlgorithm ^PrivateKey pk))))))
