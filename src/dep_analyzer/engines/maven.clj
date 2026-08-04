(ns dep-analyzer.engines.maven
  (:require [dep-analyzer.protocols :refer [DependencyEngine]]
            [clojure.string :as str])
  (:import [org.apache.maven.model.building DefaultModelBuilderFactory DefaultModelBuildingRequest FileModelSource ModelSource StringModelSource]
           [org.apache.maven.model.resolution ModelResolver]
           [org.apache.maven.model Parent]
           [java.io File]
           [java.net URL HttpURLConnection]
           [java.nio.file Files Paths StandardCopyOption]))

(defn- get-m2-path [group-id artifact-id version]
  (let [home (System/getProperty "user.home")
        group-dir (str/replace group-id "." "/")]
    (Paths/get home (into-array String [".m2" "repository" group-dir artifact-id version (str artifact-id "-" version ".pom")]))))

(defn- make-hybrid-resolver []
  (reify ModelResolver
    (^ModelSource resolveModel [this ^String group-id ^String artifact-id ^String version]
      (let [local-pom-path (get-m2-path group-id artifact-id version)]
        (if (Files/exists local-pom-path (into-array java.nio.file.LinkOption []))
          (FileModelSource. (.toFile local-pom-path))
          (try
            (let [url-str (format "https://maven.org"
                                  (str/replace group-id "." "/")
                                  artifact-id version artifact-id version)
                  connection ^HttpURLConnection (.openConnection (URL. url-str))
                  _ (.setRequestMethod connection "GET")
                  response-code (.getResponseCode connection)]
              (if (and (= response-code 200) (str/includes? (str (.getContentType connection)) "xml"))
                (let [temp-file (File/createTempFile "maven-parent-" ".pom")]
                  (.deleteOnExit temp-file)
                  (with-open [in (.getInputStream connection)]
                    (Files/copy in (.toPath temp-file) (into-array StandardCopyOption [StandardCopyOption/REPLACE_EXISTING])))
                  (FileModelSource. temp-file))
                (throw (RuntimeException. (str "Artifact not found on Central (" response-code ")")))))
            (catch Exception e
              (throw (RuntimeException. (str "Unresolvable model layout: " (.getMessage e)))))))))
    (^ModelSource resolveModel [this ^Parent parent]
      (.resolveModel this (.getGroupId parent) (.getArtifactId parent) (.getVersion parent)))
    (addRepository [this repository] nil)
    (addRepository [this repository replace] nil)
    (newCopy [this] this)))

(deftype MavenEngine []
  DependencyEngine
  (can-parse? [_ filename] (= filename "pom.xml"))
  
  (parse-dependencies [_ file-content context] ; Changed from file-path to file-content string
    (try
      (let [factory (DefaultModelBuilderFactory.)
            builder (.newInstance factory)
            request (DefaultModelBuildingRequest.)
            system-props (System/getProperties)
            ;; Wrap the XML string into an official Maven source memory object container
            string-source (StringModelSource. file-content "pom.xml")]
        
        (.. request
            (setProcessPlugins false)
            (setTwoPhaseBuilding false)
            (setValidationLevel org.apache.maven.model.building.ModelBuildingRequest/VALIDATION_LEVEL_MINIMAL)
            (setModelSource string-source) ; <-- Injected directly from memory buffer
            (setSystemProperties system-props)
            (setModelResolver (make-hybrid-resolver)))
        
        (let [building-result (.build builder request)
              effective-model (.getEffectiveModel building-result)
              dependencies (.getDependencies effective-model)]
          
          (mapv (fn [dep]
                  {:configuration "implementation"
                   :group (.getGroupId dep)
                   :artifact (.getArtifactId dep)
                   :version (or (.getVersion dep) "unspecified")})
                dependencies)))
      (catch Exception e
        (println "Error building effective Maven POM structure:" (.getMessage e))
        []))))
