# Kubernetes Patch Strategies

The official Kubernetes documentation has great information about patches and
their uses [here][k8s-doc]. This document it's an overview to what to expect and
how to use them.

## JSON Patch RFC-6902
This is the simpler patch Kubernetes supports. You have the full description
[here][rfc6902]. The `op` field can be `add`, `remove`, `replace`, `move`,
`copy` and `test`.

The `path` is a slash-separated field list to get to the value you desire to
patch, described by the [JSON Pointer RFC-6901][rfc6901]. You can use `~0` and
`~1` to describe fields with `~` and `/` characters, respectively.

The `value` field is usually the value you want to add in our manifest.

In the Kubernetes API, this is done by setting Content-Type header to
`application/json-patch+json`, and in this library is defined by the action
`:patch/json`.

This style of patching is quite good when you only want to change a simple field,
like changing `replicas` in a Deployment or adding a specific label.

```clojure
(k8s/invoke c {:kind :Deployment
               :action :patch/json
               :request {:name "nginx-deployment"
                         :namespace "default"
                         :body [{:op "add"
                                 :path "/spec/replicas"
                                 :value 2}]}})

```
## JSON Merge Patch RFC-7286
This patch can be used for the same purpose of the JSON Patch, but has some
conceptual differences. You have the full description [here][rfc7386].

The body of the request is similar to a diff, meaning that you only need to
describe what is going to change. This strategy tries to move away from the JSON
Patch imperative approach by allowing you to describe a data structure similar
to the one you're patching.

In the Kubernetes API, this is done by setting Content-Type header to
`application/merge-patch+json`, and in this library is defined by the action
`:patch/json-merge`.


```clojure
(k8s/invoke c {:kind :Deployment
               :action :patch/json-merge
               :request {:name "nginx-deployment"
                         :namespace "default"
                         :body {:kind "Deployment"
                                :api-version "apps/v1"
                                :spec {:template {:spec {:containers [{:name "sidecar"
                                                                       :image "sidecar:v2"
                                                                       :ports [{:container-port 8080}]}
                                                                      {:name "nginx"
                                                                       :image "nginx:1.14.2"
                                                                       :ports [{:container-port 80}]}]}}}}}})
```

Note that you don't have to explicit set all fields from the Deployment, only
those that are relevant for your patch. Also note that for lists, it will
substitute the whole list, so you need to be careful.

## Strategic Merge Patch

Since neither of the other patch strategies have a good way to deal with lists,
Kubernetes decided to introduce a new "strategic" merge patch. It is "strategic"
meaning that it knows enough about the manifest's structure to make decisions
about how to merge them. Read more [here][notes-on-the-strategic-merge-patch].

Note that this strategy is not available for Custom Resource Definitions yet.

```clojure
(k8s/invoke c {:kind :Deployment
               :action :patch/strategic
               :request {:name "nginx-deployment"
                         :namespace "default"
                         :body {:kind "Deployment"
                                :spec {:template {:spec {:containers [{:name "nginx"
                                                                       :image "nginx:1.14.1"
                                                                       :ports [{:name "metrics"
                                                                                :container-port 80}]}]}}}}}})
```

Note that this strategy allow you to customize containers inside a Deployment,
without having to worry about the order they are defined or the existence of
other containers.

In the Kubernetes API, this is done by setting Content-Type header to
`application/strategic-merge-patch+json`, and in this library is defined by the
action `:patch/strategic`.

## Server-Side Apply

This strategy is similar in the way you describe the change, but adds a layer of
ownership of fields, automatically adding values to `metadata.managedFields`
field. This allows you to identify conflicting changes from multiple sources.
Read more [here][server-side-apply]

In the Kubernetes API, this is done by setting Content-Type header to
`application/apply-patch+yaml`, and in this library is defined by the
action `:apply/server`.

[k8s-doc]: https://kubernetes.io/docs/tasks/manage-kubernetes-objects/update-api-object-kubectl-patch/
[rfc6902]: https://www.rfc-editor.org/rfc/rfc6902
[rfc6901]: https://www.rfc-editor.org/rfc/rfc6901
[rfc7386]: https://www.rfc-editor.org/rfc/rfc7386
[notes-on-the-strategic-merge-patch]: https://kubernetes.io/docs/tasks/manage-kubernetes-objects/update-api-object-kubectl-patch/#notes-on-the-strategic-merge-patch

[server-side-apply]: https://kubernetes.io/docs/reference/using-api/server-side-apply/
