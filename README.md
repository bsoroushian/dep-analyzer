# Dependency Analyzer (`dep-analyzer`)

A lightweight, high-performance Clojure command-line utility that extracts, deduplicates, and classifies software dependencies from Java and Kotlin codebases. 

The utility supports traversing a **local project directory** or **cloning a remote Git repository completely inside RAM** over SSH using an in-memory object database graph.

## 🚀 Features

- **Multi-Build Engine Parsing**: Automatically detects and extracts dependencies from Maven (`pom.xml`) and Gradle (`build.gradle`, `build.gradle.kts`) build scripts.
- **In-Memory Git Streams**: Connects to remote Git repositories over SSH, slicing out dependency data straight from the object database directly into RAM—no local clone required.
- **Deduplication Engine**: Automatically combines identical dependencies across multi-module projects, handles version conflicts, and groups configurations.
- **Dual Format Output Pipeline**:
  - Human-friendly, dynamic terminal aligned tables (`stdout`).
  - Machine-readable, structurally compliant **CycloneDX v1.6 SBOM JSON format**.

## 🛠️ Prerequisites

- **Java Development Kit (JDK)**: Version 11 or later.
- **Leiningen**: The Clojure automation and project coordination framework installed on your system path.
- **SSH Credentials**: A local SSH key paired with your Git provider (e.g., GitHub, GitLab) for memory repository streaming.

## 🏃 Getting Started & Usage

Navigate to your root project directory and execute the following Leiningen workflows.

### Run with Local Folders
```bash
# Print a beautiful visual table to stdout
lein run /path/to/your/java-or-kotlin-project

# Force explicit table layout
lein run /path/to/your/java-or-kotlin-project --format table
```

### Run with In-Memory Git Repositories
```bash
# Export structurally compliant CycloneDX JSON
lein run git@github.com:spring-projects/spring-petclinic.git --format json
```

### Clean Pipeline Processing with `jq`
Operational logs, connection indicators, and progress bars are routed directly to `stderr`. This ensures your `stdout` pipes remain uncorrupted for direct piping:
```bash
lein run git@github.com:spring-projects/spring-petclinic.git --format json | jq .
```

---

## 🧪 Testing Protocol

The testing framework follows standard Clojure mechanics (*"No news is good news"*). To run the active unit test suites:

```bash
lein test
```

---

## 🏗️ Architecture Blueprint

The project separates layout parsing from file extraction to maintain a low memory footprint:

- `src/dep_analyzer/core.clj`: Application entry point (`-main`), CLI argument processing, dependency deduplication logic, and output formatting.
- `src/dep_analyzer/protocols.clj`: Defines the standard `DependencyEngine` blueprint ensuring structural decoupling.
- `src/dep_analyzer/retrieval.clj`: Manages JGit network interactions, SSH authorization bindings, and RAM-bound object database traversals.
- `src/dep_analyzer/engines/`:
  - `gradle.clj`: Regex-driven parser that extracts local property mappings, configurations, and core scope parameters.
  - `maven.clj`: Injects memory buffers into the official Maven Model Engine factory using an active local `.m2` repository fallback resolver.
