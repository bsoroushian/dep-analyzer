(ns dep-analyzer.protocols)

(defprotocol DependencyEngine
  (can-parse? [this filename] 
    "Returns true if this engine handles this specific file target.")
  (parse-dependencies [this file-content context]
    "Reads a file's content string and returns a sequence of clean dependency maps."))
