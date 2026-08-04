(defproject dep-analyzer "0.1.0-SNAPSHOT"
  :description "Static analysis engine tool for tracking Java/Kotlin dependencies."
  :url "https://github.com" ;; Updated to match your personal URL path
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://eclipse.org"}
  :dependencies [[org.clojure/clojure "1.12.2"]
                 [org.clojure/data.json "2.5.2"]
                 [org.clojure/tools.logging "1.3.0"] ;; Added to stabilize internal Jimfs filesystem locks
                 [org.apache.maven/maven-model-builder "3.9.9"]
                 [org.apache.maven/maven-model "3.9.9"]
                 [org.apache.maven/maven-resolver-provider "3.9.9"]
                 [com.google.jimfs/jimfs "1.3.0"]
                 [org.slf4j/slf4j-nop "2.0.16"]
                 [org.eclipse.jgit/org.eclipse.jgit "7.1.0.202411261347-r"]
                 [org.eclipse.jgit/org.eclipse.jgit.ssh.apache "7.1.0.202411261347-r"]]
  :main ^:skip-aot dep-analyzer.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
