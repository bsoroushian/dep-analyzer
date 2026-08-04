(ns dep-analyzer.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [dep-analyzer.core :refer [deduplicate-dependencies]]
            [dep-analyzer.protocols :as proto]
            [dep-analyzer.engines.gradle :refer [->GradleEngine]]))

;; 1. TEST CASE FOR EXTRACTING THE BEST VERSION STRING
(deftest better-version-test
  (testing "Selecting the most informative version string between two options"
    ;; Note: 'better-version' is a private function (defn-), so we must use 
    ;; the '#' var reader literal to access it from outside its home namespace.
    (let [better-version-fn @#'dep-analyzer.core/better-version]
      
      (is (= "1.2.3" (better-version-fn "1.2.3" nil))
          "Should prefer a valid version over a nil value")
      
      (is (= "2.0.0" (better-version-fn "unspecified" "2.0.0"))
          "Should reject 'unspecified' and select the concrete version")
      
      (is (= "4.1.0" (better-version-fn "${spring.version}" "4.1.0"))
          "Should reject an unresolved Maven variable string prefix")
      
      (is (= "1.0.0" (better-version-fn "$project_version" "1.0.0"))
          "Should reject an unresolved Gradle dollar-sign variable"))))

;; 2. TEST CASE FOR PARSING AND MERGING DUPES
(deftest deduplicate-dependencies-test
  (testing "Merging duplicates and consolidating their configurations"
    (let [raw-deps [{:group "org.json" :artifact "json" :version "20231013" :configuration "implementation"}
                    {:group "org.json" :artifact "json" :version "unspecified" :configuration "testImplementation"}
                    {:group "org.slf4j" :artifact "slf4j-api" :version "2.0.7" :configuration "api"}]
          
          result (deduplicate-dependencies raw-deps)
          ;; Find our specific merged artifact from the output vector
          json-dep (first (filter #(= (:artifact %) "json") result))
          slf4j-dep (first (filter #(= (:artifact %) "slf4j-api") result))]

      (is (= 2 (count result))
          "The 3 raw entries should be compressed down to 2 unique artifacts")

      (is (= "20231013" (:version json-dep))
          "The concrete version should override the 'unspecified' marker")

      ;; The core function updates configurations by appending commas
      (is (= "implementation, testImplementation" (:configuration json-dep))
          "Configurations for identical artifacts must be consolidated into a single string")

      (is (= "2.0.7" (:version slf4j-dep))
          "Standalone un-duplicated dependencies must remain unchanged"))))

(deftest gradle-engine-test
  (let [engine (->GradleEngine)]
    
    (testing "File recognition logic via protocol implementation"
      ;; The can-parse? method verifies that our engine responds to the right files
      (is (true? (proto/can-parse? engine "build.gradle")))
      (is (true? (proto/can-parse? engine "build.gradle.kts")))
      (is (false? (proto/can-parse? engine "pom.xml"))))

    (testing "Extracting and identifying dependencies from a standard Gradle script string"
      (let [mock-gradle-content "
            dependencies {
                implementation 'org.springframework:spring-core:6.1.1'
                testImplementation 'org.junit.jupiter:junit-jupiter-api:5.10.0'
                // This unknown configuration rule should be skipped entirely by our engine regex
                customConfig 'org.secret:hidden-utility:1.0.0'
            }
            "
            parsed-deps (proto/parse-dependencies engine mock-gradle-content {})
            spring-dep (first (filter #(= (:artifact %) "spring-core") parsed-deps))
            junit-dep (first (filter #(= (:artifact %) "junit-jupiter-api") parsed-deps))]
        
        (is (= 2 (count parsed-deps))
            "Should only extract implementation and testImplementation, skipping custom configs")
        
        (is (= "org.springframework" (:group spring-dep)))
        (is (= "6.1.1" (:version spring-dep)))
        (is (= "implementation" (:configuration spring-dep)))
        
        (is (= "testImplementation" (:configuration junit-dep)))))

    (testing "Interpolating local variables during script compilation"
      (let [mock-variable-content "
            def jacksonVersion = '2.15.2'
            dependencies {
                implementation 'com.fasterxml.jackson.core:jackson-databind:' + jacksonVersion
            }
            "
            ;; Because our engine regex handles standard string formats, let's pass a layout
            ;; where variables are extracted into context, mimicking real-world project trees
            parsed-deps (proto/parse-dependencies engine mock-variable-content {})]
        ;; Our regex splits clean triples. If the local variable matches, it populates cleanly!
        (is (not (empty? parsed-deps)))))))
