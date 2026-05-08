(ns kubernetes-api.loader.kubeconfig
  (:require [kubernetes-api.misc :as misc]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clj-yaml.core :as yaml]))


(defn cluster-info [kubeconfig cluster-name]
  (when-let [cluster (misc/find-first #(= (:name %) cluster-name) (:clusters kubeconfig))]
    (:cluster cluster)))

(defn user-info [kubeconfig user-name]
  (when-let [user (misc/find-first #(= (:name %) user-name) (:users kubeconfig))]
    (:user user)))

(defn context-info [kubeconfig context-name]
  (let [{:keys [cluster user] :as context} (:context (misc/find-first #(= (:name %) context-name) (:contexts kubeconfig)))
        cluster-info (cluster-info kubeconfig cluster)
        user-info (user-info kubeconfig user)]
    (when (and (some? context) (some? cluster-info) (some? user-info))
      {:cluster cluster-info
       :user user-info
       :context context
       ::kubeconfig-file (::kubeconfig-file kubeconfig)})))

(defn kubeconfig-files [kubeconfig]
  (let [kubeconfig-input (or kubeconfig
                             (System/getenv "KUBECONFIG")
                             (io/file (System/getProperty "user.home") ".kube" "config"))]
    (cond
      (string? kubeconfig-input) (map io/file (string/split kubeconfig-input #":"))
      (sequential? kubeconfig-input) (map io/file kubeconfig-input)
      (instance? java.io.File kubeconfig-input) [kubeconfig-input])))

(defn- kubeconfig-data [kubeconfig]
  (->> (kubeconfig-files kubeconfig)
       (mapv (fn [kubeconfig-file] (assoc (yaml/parse-string (slurp kubeconfig-file))
                                          ::kubeconfig-file kubeconfig-file)))))


(defn context
  "Load a context from kubeconfig file(s) and context name.

   [Options]
   :kubeconfig - optional kubeconfig file(s), default to KUBECONFIG env var or
                 ~/.kube/config.  It can be a string or a list of strings.
   :context - optional context name, defaults to :current-context or \"default\"
   "
  [{:keys [kubeconfig context] :as _options}]
  (->> (kubeconfig-data kubeconfig)
       (keep (fn [kubeconfig-data]
                     (context-info kubeconfig-data (or context
                                                  (:default-context kubeconfig-data)
                                                  "default"))))
       first))
