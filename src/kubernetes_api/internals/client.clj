(ns kubernetes-api.internals.client
  (:require [camel-snake-kebab.core :as csk]
            [clojure.string :as string]
            [kubernetes-api.misc :as misc]))

(defn pascal-case-routes [k8s]
  (update k8s :handlers
          (fn [handlers]
            (mapv #(update % :route-name csk/->PascalCase) handlers))))

(defn swagger-definition-for-route [k8s route-name]
  (->> (:handlers k8s)
       (misc/find-first #(= route-name (:route-name %)))
       :swagger-definition))

(defn handler-kind [handler]
  (-> handler :swagger-definition :x-kubernetes-group-version-kind :kind keyword))

(defn handler-group [handler]
  (-> handler :swagger-definition :x-kubernetes-group-version-kind :group))

(defn handler-version [handler]
  (-> handler :swagger-definition :x-kubernetes-group-version-kind :version))

(defn handler-action [handler]
  (-> handler :swagger-definition :x-kubernetes-action keyword))

(defn all-namespaces-route? [route-name]
  (string/ends-with? (name route-name) "ForAllNamespaces"))

(defn scale-resource [route-name]
  (second (re-matches #".*Namespaced([A-Za-z]*)Scale" (name route-name))))

(defn kind
  "Returns a kubernetes-api kind. Similar to handler-kind, but deals with some
  corner-cases. Returns a keyword, that is namespaced only if there's a
  subresource.

  Example:
  Deployment/Status"
  [{:keys [route-name] :as handler}]
  (let [kind (some-> (handler-kind handler) name)]
    (cond
      (string/ends-with? (name route-name) "Status") (keyword kind "Status")
      (string/ends-with? (name route-name) "Scale") (keyword (scale-resource route-name) "Scale")
      :else (keyword kind))))

(defn action
  "Return a kubernetes-api action. Similar to handler-action, but tries to be
  unique for each kind.

  Example:
  :list-all"
  [{:keys [route-name method] :as handler}]
  (cond
    (re-matches #"Create.*NamespacedPodBinding" (name route-name)) :pod/create
    (re-matches #"Connect.*ProxyWithPath" (name route-name)) (keyword "connect.with-path" (name method))
    (re-matches #"Connect.*Proxy" (name route-name)) (keyword "connect" (name method))
    (re-matches #"Connect.*" (name route-name)) (keyword "connect" (name method))
    (re-matches #"ReplaceCertificates.*CertificateSigningRequestApproval" (name route-name)) :replace-approval
    (re-matches #"Read.*NamespacedPodLog" (name route-name)) :misc/logs
    (re-matches #"Replace.*NamespaceFinalize" (name route-name)) :misc/finalize
    (all-namespaces-route? route-name) (keyword (str (name (handler-action handler)) "-all"))
    :else (handler-action handler)))

(defn find-route [k8s {:keys [all-namespaces?] :as _search-params
                       search-kind :kind
                       search-action :action}]
  (->> (:handlers k8s)
       (filter (fn [handler]
                 (and (or (= (keyword search-kind) (kind handler)) (nil? search-kind))
                      (or (= (keyword search-action) (action handler)) (nil? search-action))
                      (= (boolean all-namespaces?) (all-namespaces-route? (:route-name handler))))))
       (map :route-name)))

(defn version-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :version))

(defn group-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :group))

(defn kind-of [k8s route-name]
  (->> (swagger-definition-for-route k8s route-name)
       :x-kubernetes-group-version-kind
       :kind
       keyword))

(defn core-versions [k8s]
  (mapv
   #(hash-map :name ""
              :versions [{:groupVersion % :version %}]
              :preferredVersion {:groupVersion % :version %})
   (:versions (:kubernetes-api.core/core-api-versions k8s))))

(defn all-versions [k8s]
  (concat (:groups (:kubernetes-api.core/api-group-list k8s))
          (core-versions k8s)))

(defn ^:private choose-preffered-version [k8s route-names]
  (misc/find-first
   (fn [route]
     (some #(and (= (:name %) (group-of k8s route))
                 (= (:version (:preferredVersion %)) (version-of k8s route)))
           (all-versions k8s)))
   route-names))

(defn find-preferred-route [k8s search-params]
  (->> (find-route k8s search-params)
       (filter (fn [x] (not (string/ends-with? (name x) "Status"))))
       ((partial choose-preffered-version k8s))))

(defn preffered-version? [k8s handler]
  (let [preffered-route (find-preferred-route k8s {:kind   (handler-kind handler)
                                                   :action (handler-action handler)})]
    (and (= (handler-version handler) (version-of k8s preffered-route))
         (= (handler-group handler) (group-of k8s preffered-route)))))

