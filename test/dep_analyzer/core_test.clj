(ns dep-analyzer.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [dep-analyzer.core :refer [deduplicate-dependencies]]))

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
