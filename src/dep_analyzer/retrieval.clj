(ns dep-analyzer.retrieval
  (:import [org.eclipse.jgit.api Git TransportConfigCallback]
           [org.eclipse.jgit.internal.storage.dfs DfsRepositoryDescription InMemoryRepository]
           [org.eclipse.jgit.transport SshTransport Transport]
           [org.eclipse.jgit.transport.sshd SshdSessionFactory]
           [org.eclipse.jgit.lib ObjectId FileMode]
           [org.eclipse.jgit.revwalk RevWalk]
           [org.eclipse.jgit.treewalk TreeWalk]
           [java.io ByteArrayOutputStream]))

(defn- configure-ssh-auth [command]
  (let [ssh-factory (SshdSessionFactory.)]
    (.setTransportConfigCallback command
       (reify TransportConfigCallback
         ;; Explicitly type hint both the method return (^void) and the parameter type (^Transport)
         (^void configure [_ ^Transport transport]
           (when (instance? SshTransport transport)
             (.setSshSessionFactory ^SshTransport transport ssh-factory)))))))

(defn- extract-files-from-object-db [repo head-id]
  (let [git-db (.getRepository repo)]
    (with-open [walk (RevWalk. git-db)
                tree-walk (TreeWalk. git-db)]
      (let [commit (.parseCommit walk head-id)
            tree (.getTree commit)]
        (.addTree tree-walk tree)
        (.setRecursive tree-walk false)
        (loop [results []]
          (if (.next tree-walk)
            (let [path (.getPathString tree-walk)
                  mode (.getFileMode tree-walk)
                  filename (clojure.string/replace path #".*/" "")]
              (cond
                (= mode FileMode/TREE)
                (do (.enterSubtree tree-walk) (recur results))
                
                (or (= filename "pom.xml") (= filename "build.gradle") (= filename "build.gradle.kts"))
                (let [object-id (.getObjectId tree-walk 0)
                      loader (.open git-db object-id)
                      out (ByteArrayOutputStream.)]
                  (.copyTo loader out)
                  (recur (conj results {:path path :content (.toString out "UTF-8")})))
                
                :else (recur results)))
            results))))))

(defn clone-repo-to-mem [repo-url]
  (try
    (let [repo-desc (DfsRepositoryDescription. "in-memory-repo")
          in-mem-repo (InMemoryRepository. repo-desc)
          git (Git. in-mem-repo)
          fetch-cmd (.fetch git)
          ref-spec (org.eclipse.jgit.transport.RefSpec. "+refs/heads/*:refs/remotes/origin/*")]
      
      ;; Redirect logs to stderr so they don't break stdout pipes
      (binding [*out* *err*]
        (println "Connecting to remote repository completely inside RAM over SSH..."))
      
      (.setRemote fetch-cmd repo-url)
      (.setRefSpecs fetch-cmd (into-array org.eclipse.jgit.transport.RefSpec [ref-spec]))
      (configure-ssh-auth fetch-cmd)
      
      (let [fetch-result (.call fetch-cmd)
            head-ref (.getAdvertisedRef fetch-result "HEAD")]
        (if-not head-ref
          (do 
            (binding [*out* *err*]
              (println "Could not resolve a default branch reference (HEAD) on the target repository.")) 
            [])
          (let [head-id (.getObjectId head-ref)]
            (binding [*out* *err*]
              (println "Network stream sync complete! Slicing build modules out of object graph..."))
            (extract-files-from-object-db git head-id)))))
    (catch Exception e
      (binding [*out* *err*]
        (println "In-Memory Git SSH clone operation failed:" (.getMessage e)))
      [])))
