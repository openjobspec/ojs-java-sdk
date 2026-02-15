# Examples

Standalone examples demonstrating OJS Java SDK usage. Each file is a self-contained program.

## Prerequisites

- An OJS-compatible server running at `http://localhost:8080`
  (e.g., [ojs-backend-redis](https://github.com/openjobspec/ojs-backend-redis))

## Running with JBang

The easiest way to run examples is with [JBang](https://www.jbang.dev/):

```bash
# Install JBang (if not already installed)
curl -Ls https://sh.jbang.dev | bash -s - app setup

# Run an example
jbang BasicEnqueue.java
jbang WorkerProcessing.java
jbang WorkflowChain.java
```

## Running with Java directly

Build the SDK first, then compile and run:

```bash
# From the project root
mvn clean package -DskipTests

# Run an example
java -cp target/ojs-sdk-0.1.0.jar examples/BasicEnqueue.java
```

## Examples

| File | Description |
|------|-------------|
| `BasicEnqueue.java` | Client producer: enqueue jobs with options, get status, cancel |
| `WorkerProcessing.java` | Consumer: register handlers, middleware, graceful shutdown |
| `WorkflowChain.java` | Workflows: chain, group, and batch patterns |
