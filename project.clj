(defproject nubank/k8s-api "1.1.0-SNAPSHOT"
  :description "A library to talk with kubernetes api"
  :url "https://github.com/nubank/k8s-api"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :plugins [[lein-cljfmt "0.5.7"]
            [lein-kibit "0.1.6"]
            [lein-nsorg "0.2.0"]]
  :cljfmt {:indents {providing [[:inner 0]]}}
  :dependencies [[org.clojure/clojure "1.11.0"]
                 [com.github.oliyh/martian "0.1.26"]
                 [com.github.oliyh/martian-httpkit "0.1.26"]
                 [less-awful-ssl "1.0.6"]
                 [http-kit/http-kit "2.8.0-alpha3"]]
  :main ^:skip-aot kubernetes-api.core
  :resource-paths ["resources"]
  :target-path "target/%s"
  :aliases {"lint"     ["do" ["cljfmt" "check"] ["nsorg"] ["kibit"]]
            "lint-fix" ["do" ["cljfmt" "fix"] ["nsorg" "--replace"] ["kibit" "--replace"]]}
  :profiles {:uberjar {:aot :all}
             :dev {:resource-paths ["test/kubernetes_api/resources"]
                   :dependencies [[nubank/matcher-combinators "3.8.5"]
                                  [nubank/mockfn "0.7.0"]]}})
