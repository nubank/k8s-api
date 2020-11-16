# kubernetes-api

kubernetes-api is a Clojure library that acts as a kubernetes client

## Motivation

We had a good experience with
 [cognitect-labs/aws-api](https://github.com/cognitect-labs/aws-api), and missed
 something like that for Kubernetes API. We had some client libraries that
 generated a lot of code, but it lacked discoverability and documentation.

### clojure.deps
```clojure
{:deps {nubank/k8s-api {:mvn/version "0.1.2"}}}
```

### Leiningen
```clojure
[nubank/k8s-api "0.1.2"]
```

```clojure
;; In your ns statement
(ns my.ns
  (:require [kubernetes-api.core :as k8s]))
```


## Usage
### Instantiate a client

There're multiple options for authentication while instantiating a client. You
can explicit set a token:
```clojure
(def k8s (k8s/client "http://some.host" {:token "..."}))
```

Or a function that returns the token

```clojure
(def k8s (k8s/client "http://some.host" {:token-fn (constantly "...")}))
```

You can also define client certificates
```clojure
(def k8s (k8s/client "http://some.host" {:ca-cert     "/some/path/ca-docker.crt"
                                         :client-cert "/some/path/client-cert.pem"
                                         :client-key  "/some/path/client-java.key"}))
```

### Discover
You can list all operations with
```clojure
(k8s/explore k8s)
```

or specify a specific entity
```clojure
(k8s/explore k8s :Deployment)
;=>
[:Deployment
 [:get "read the specified Deployment"]
 [:update "replace the specified Deployment"]
 [:delete "delete a Deployment"]
 [:patch "partially update the specified Deployment"]
 [:list "list or watch objects of kind Deployment"]
 [:create "create a Deployment"]
 [:deletecollection "delete collection of Deployment"]
 [:list-all "list or watch objects of kind Deployment"]]
```

get info on an operation
```clojure
(k8s/info k8s {:kind :Deployment
               :action :create})
;=>
{:summary "create a Deployment",
 :parameters {:namespace java.lang.String,
              #schema.core.OptionalKey{:k :pretty} (maybe Str),
              #schema.core.OptionalKey{:k :dry-run} (maybe Str),
              #schema.core.OptionalKey{:k :field-manager} (maybe Str),
              :body ...},
 :returns {200 {#schema.core.OptionalKey{:k :apiVersion} (maybe Str),
                #schema.core.OptionalKey{:k :kind} (maybe Str),
                #schema.core.OptionalKey{:k :metadata} ...,
                #schema.core.OptionalKey{:k :spec} ...,
                #schema.core.OptionalKey{:k :status} ...},
           201 {#schema.core.OptionalKey{:k :apiVersion} (maybe Str),
                #schema.core.OptionalKey{:k :kind} (maybe Str),
                #schema.core.OptionalKey{:k :metadata} ...,
                #schema.core.OptionalKey{:k :spec} ...,
                #schema.core.OptionalKey{:k :status} ...},
           202 {#schema.core.OptionalKey{:k :apiVersion} (maybe Str),
                #schema.core.OptionalKey{:k :kind} (maybe Str),
                #schema.core.OptionalKey{:k :metadata} ...,
                #schema.core.OptionalKey{:k :spec} ...,
                #schema.core.OptionalKey{:k :status} ...},
           401 Any}}
```


### Invoke

You can call an operation with
```clojure
(k8s/invoke k8s {:kind    :ConfigMap
                 :action  :create
                 :request {:namespace "default"
                           :body      {:apiVersion "v1"
                                       :data       {"foo" "bar"}}}})
```

invoke it will return the body, with `:request` and `:response` in metadata
```clojure
(meta (k8s/invoke ...))
;=>
{:request ...
 :response {:status ...
            :body ...}}
```

You can debug the request map with
```clojure
(k8s/request k8s {:kind    :ConfigMap
                  :action  :create
                  :request {:namespace "default"
                            :body      {:apiVersion "v1"
                                        :data       {"foo" "bar"}}}})
```
