(defproject kubernetes-api "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [martian "0.1.12-SNAPSHOT"]
                 [martian-httpkit "0.1.11"]
                 [less-awful-ssl "1.0.4"]]
  :main ^:skip-aot kubernetes-api.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
