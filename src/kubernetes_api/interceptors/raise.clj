(ns kubernetes-api.interceptors.raise)

(defn- status-error? [status]
  (or (nil? status) (>= status 400)))

(def ^:private error-type->status-code
  {:bad-request                   400
   :invalid-input                 400
   :unauthorized                  401
   :payment-required              402
   :forbidden                     403
   :not-found                     404
   :method-not-allowed            405
   :not-acceptable                406
   :proxy-authentication-required 407
   :timeout                       408
   :conflict                      409
   :gone                          410
   :length-required               411
   :precondition-failed           412
   :payload-too-large             413
   :uri-too-long                  414
   :unsupported-media-type        415
   :range-not-satisfiable         416
   :expectation-failed            417
   :unprocessable-entity          422
   :locked                        423
   :upgrade-required              426
   :too-many-requests             429
   :server-error                  500
   :not-implemented               501
   :bad-gateway                   502
   :service-unavailable           503
   :gateway-timeout               504
   :http-version-not-supported    505})

(def ^:private status-code->error-type (zipmap (vals error-type->status-code) (keys error-type->status-code)))

(defn- make-exception [{:keys [status] :as response}]
  (ex-info (str "APIServer error: " status)
           {:type (status-code->error-type status)
            :response response}))

(defn check-response
  "Checks the status code. If 400+, raises an exception, returns body otherwise"
  [response]
  (cond
    (:error response) (throw (:error response))
    (status-error? (:status response)) (throw (make-exception response))
    :else (:body response)))

(defn- maybe-assoc-error [{:keys [error status body] :as response}]
  (cond
    error                  (assoc response :kubernetes-api.core/error error)
    (status-error? status) (assoc response :kubernetes-api.core/error (make-exception response))
    :else body))

(defn new [_]
  {:name  ::raise
   :leave (fn [{:keys [request response]}]
            (with-meta
              {:response (maybe-assoc-error response)}
              {:response response :request request}))})
