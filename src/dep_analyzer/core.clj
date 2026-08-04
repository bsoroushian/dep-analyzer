(ns dep-analyzer.core
  (:require [dep-analyzer.protocols :as proto]
            [dep-analyzer.engines.gradle :refer [->GradleEngine]]
            [dep-analyzer.engines.maven :refer [->MavenEngine]]
            [dep-analyzer.retrieval :as retrieval]
            [clojure.pprint :refer [pprint]]
            [clojure.data.json :as json]
            [clojure.string :as string]) ;; Added an explicit alias to prevent namespace reference crashes
  (:import [java.nio.file Files Paths FileVisitOption])
  (:gen-class))

(def ^:private active-engines [(->GradleEngine) (->MavenEngine)])

(def ^:private get-engine (memoize (fn [filename] (first (filter #(proto/can-parse? % filename) active-engines)))))

(defn- read-local-target-files [root-path-str]
  "Walks a local folder path and returns a uniform list map: {:path '...' :content '...'}"
  (try
    (let [root-path (Paths/get root-path-str (into-array String []))]
      (with-open [stream (Files/walk root-path (into-array FileVisitOption []))]
        (->> (iterator-seq (.iterator stream))
             (filter #(let [filename (.. % getFileName toString)]
                        (or (= filename "pom.xml") (= filename "build.gradle") (= filename "build.gradle.kts"))))
             (mapv (fn [p] {:path (.toString p) :content (Files/readString p)})))))
    (catch Exception e
      (println "Error traversing folder system paths:" (.getMessage e))
      [])))

(defn- better-version [v1 v2]
  (cond (nil? v1) v2 (nil? v2) v1
        (or (= v1 "unspecified") (string/starts-with? v1 "$") (string/starts-with? v1 "{")) v2
        :else v1))

(defn deduplicate-dependencies [deps-list]
  (->> deps-list
       (group-by (juxt :group :artifact))
       (map (fn [[[_group _artifact] grouped-records]]
              (reduce (fn [acc item]
                        (-> acc
                            (assoc :version (better-version (:version acc) (:version item)))
                            (update :configuration #(if (= % (:configuration item)) % (str % ", " (:configuration item))))))
                      (first grouped-records)
                      grouped-records)))
       vec))

(defn- process-project-files [files-map-list]
  (reduce (fn [acc {:keys [path content]}]
            (let [filename (string/replace path #".*/" "")
                  context {}]
              (if-let [engine (get-engine filename)]
                (into acc (proto/parse-dependencies engine content context))
                acc)))
          []
          files-map-list))

(defn- build-cyclonedx-bom [clean-dependencies]
  {:bomFormat "CycloneDX"
   :specVersion "1.6"
   :version 1
   :metadata {:timestamp (str (java.time.Instant/now))
              :tools {:components [{:type "application"
                                    :author "Clojure Utility"
                                    :name "dep-analyzer"
                                    :version "1.0.0"}]}}
   :components (mapv (fn [dep]
                       (let [config-str (string/lower-case (or (:configuration dep) ""))
                             ;; Fix: Checks if the config contains "test" anywhere, 
                             ;; while ignoring whether "implementation" or "api" is part of the name (e.g. testImplementation)
                             is-test? (or (string/includes? config-str "test")
                                          (string/includes? config-str "testimplementation"))]
                         {:type "library"
                          :bom-ref (str "pkg:maven/" (:group dep) "/" (:artifact dep) "@" (:version dep))
                          :group (:group dep)
                          :name (:artifact dep)
                          :version (:version dep)
                          :purl (str "pkg:maven/" (:group dep) "/" (:artifact dep) "@" (:version dep))
                          :scope (if is-test? "excluded" "required")})) 
                     clean-dependencies)})

;; NEW: Helper function to map flat CLI arguments into an structured configuration map
(defn- parse-cli-args [args]
  (let [arg-str (string/join " " args)
        format-match (re-find #"--format\s+([^\s]+)" arg-str)
        output-format (if format-match (second format-match) "table") ;; default fallback to visual table
        ;; Strip away flags to target the lone positional argument (the source target string)
        target-input (first (filter #(not (string/starts-with? % "--")) args))]
    {:target target-input
     :format output-format}))

(defn -main [& args]
  (let [{:keys [target format]} (parse-cli-args args)]
    (if-not target
      (println "Please provide a folder path or Git SSH URL!\nUsage: lein run <path/url> [--format table|json]")
      (let [is-git-url? (or (string/starts-with? target "git@")
                            (string/starts-with? target "ssh://")
                            (string/ends-with? target ".git"))
            
            project-files (if is-git-url?
                            (retrieval/clone-repo-to-mem target)
                            (read-local-target-files target))]
        
        (if (empty? project-files)
          (println "No build targets or dependencies files extracted.")
          (let [_ (binding [*out* *err*] ;; Redirect progress log to stderr
                    (println "Scanning target context... Processing" (count project-files) "modules."))
                raw-dependencies (process-project-files project-files)
                clean-dependency-tree (deduplicate-dependencies raw-dependencies)]
            
            (cond
              (= format "json")
              (let [cyclonedx-map (build-cyclonedx-bom clean-dependency-tree)]
                (println (json/write-str cyclonedx-map :value-fn (fn [k v] v) :escape-slash false)))
              
              :else
              (do
                (println "\n=== DETECTED PROJECT DEPENDENCIES ===")
                (clojure.pprint/print-table [:group :artifact :version :configuration] clean-dependency-tree)))))))))
