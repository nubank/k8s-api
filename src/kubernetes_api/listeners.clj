(ns kubernetes-api.listeners
  (:refer-clojure :exclude [delay])
  (:require [kubernetes-api.core :as k8s-api]
            [martian.core :as martian])
  (:import (java.util UUID)
           (java.util.concurrent Executors TimeUnit)))

(defn- new-executor [size] (Executors/newScheduledThreadPool size))

(defn schedule-with-delay-seconds [pool runnable num-seconds]
  (.schedule pool runnable num-seconds TimeUnit/SECONDS))

(defn schedule-periodic-seconds [pool runnable num-seconds]
  (.scheduleAtFixedRate pool runnable num-seconds num-seconds TimeUnit/SECONDS))

(defn cancel-task [task]
  (.cancel task true))

(defn task-cancelled? [task]
  (.isCancelled task))

(defn delay [task]
  (.getDelay task TimeUnit/SECONDS))

(defn status-task [task]
  (cond
    (task-cancelled? task) :cancelled
    :else :registered))

(defn status [{:keys [state] :as context} listener-id]
  (let [listener (get-in @state [:listeners listener-id])]
    {:id     listener-id
     :status (status-task (:task listener))}))

(defn new-context
  "Creates a context for running listeners for kubernetes objects

  client: kubernetes-api client
  thread-pool-size: number of threads for polling
  polling-rate: seconds of delay between requests"
  [{:keys [client thread-pool-size polling-rate]
    :or   {thread-pool-size 1
           polling-rate     1}}]
  {:client           client
   :thread-pool-size thread-pool-size
   :polling-rate     polling-rate
   :executer         (new-executor thread-pool-size)
   :state            (atom {:listeners {}})})

(defn random-uuid []
  (UUID/randomUUID))

(defn action [kind]
  (case kind
    :Deployment :ReadAppsV1NamespacedDeployment))

(defn handler-fn
  [{:keys [client state] :as _context}
   {:keys [id kind namespace name] :as params}
   listener-fn]
  (fn []
    (let [current-version (get-in @state [:listeners id :version])
          resp            (k8s-api/invoke client params)
          new-version     (get-in resp [:metadata :resourceVersion])]
      (when (not= current-version new-version)
        (listener-fn resp)
        (swap! state
               (fn [st]
                 (assoc-in st [:listeners id :version] new-version)))))))

(defn register
  [{:keys [executer state polling-rate] :as context}
   params
   listener-fn]
  (let [listener-id (random-uuid)]
    (swap! state
           (fn [s]
             (assoc-in s [:listeners listener-id :task]
                       (schedule-periodic-seconds executer
                                                  (handler-fn context params listener-fn)
                                                  polling-rate))))
    listener-id))

(defn print-version
  [deployment]
  (prn (get-in deployment [:metadata :resourceVersion])))

(defn deregister
  [{:keys [state] :as context}
   id]
  (cancel-task (get-in @state [:listeners id :task])))

