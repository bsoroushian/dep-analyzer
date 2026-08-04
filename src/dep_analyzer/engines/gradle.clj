(ns dep-analyzer.engines.gradle
  (:require [dep-analyzer.protocols :refer [DependencyEngine]]
            [clojure.string :as str]))

(defn- extract-local-variables [content]
  (let [var-regex #"(?:val|const\s+val|def)\s+(\w+)\s*=\s*[\"']([^\"']+)[\"']"]
    (->> (re-seq var-regex content)
         (reduce (fn [acc [_ var-name var-value]] (assoc acc var-name var-value)) {}))))

(defn- resolve-version [version-str properties]
  (let [clean-key (str/replace version-str #"^\$" "")]
    (get properties clean-key version-str)))

(defn- extract-clean-deps [content properties]
  (let [valid-configs #{"implementation" "api" "classpath" "testImplementation" "runtimeOnly" "compileOnly"}
        gradle-regex #"(\w+)\s*\(?\s*[\"']([^\"':]+):([^\"':]+):?([^\"']*)?[\"']\s*\)?"]
    (->> (re-seq gradle-regex content)
         (filter (fn [[_ config _ _ _]] (contains? valid-configs config)))
         (mapv (fn [[_ config group artifact version]]
                 (let [raw-version (if (str/blank? version) "unspecified" version)]
                   {:configuration config
                    :group group
                    :artifact artifact
                    :version (resolve-version raw-version properties)}))))))

(deftype GradleEngine []
  DependencyEngine
  (can-parse? [_ filename] (or (= filename "build.gradle") (= filename "build.gradle.kts")))
  
  (parse-dependencies [_ file-content context] ; Changed from file-path to file-content string
    (try
      (let [local-vars (extract-local-variables file-content)
            merged-properties (merge (:properties context {}) local-vars)]
        (extract-clean-deps file-content merged-properties))
      (catch Exception e
        (println "Error parsing Gradle layout content:" (.getMessage e))
        []))))
