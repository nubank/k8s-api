# Change Log

## 0.4.0
- Update unreleased extend-client function to use apiextensions/v1, since apiextensions/v1beta1 was deleted in newer versions of kubernetes
- Refactor extend-client code to simplify it

## 0.3.0
- Bump martian to version 0.1.26 to get fix about [parameters spec](https://github.com/oliyh/martian/pull/196)
- Add openapi configuration to disable automatic discovery (see README.md)

## 0.2.1
- Add support for JSON Patch, JSON Merge, Strategic Merge Patch and Server-Side
Apply requests.
- Change how raise interceptor works to avoid unnecessary logging

## 0.1.2
- Fixes corner cases like `misc/logs` for Pods

## 0.1.0
- Initial version
