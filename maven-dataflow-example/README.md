# Maven Dataflow Flex Template Example

This module includes:
- a simple local runner class: `DataflowFlagEvaluationExample`
- a Flex Template pipeline entrypoint: `FlexTemplateOpenFeaturePipeline`

Both use OpenFeature + flagd in FILE mode.

## Local smoke run (non-Dataflow)

```bash
APP_ENV=dev mvn exec:java
```

## Build shaded JAR for Flex Template

```bash
mvn -DskipTests package
```

JAR output:
- `target/maven-dataflow-example-0.0.1-SNAPSHOT-all.jar`

## Flex Template Container

Use:
- `flex-template/Dockerfile`
- `flex-template/metadata.json`

The Dockerfile bakes example flags into `/opt/flags`.

Required runtime parameter:
- `--flagFilePath=/opt/flags/flags-<env>.json` (must be local file path, not `gs://`)

## Typical runtime parameters

```text
--appEnv=prod
--flagFilePath=/opt/flags/flags-prod.json
--output=gs://<bucket>/openfeature-demo/output
```

Optional:
- `--input=gs://<bucket>/path/input.txt`

## Important Flex Template behavior

In Flex Template jobs, FILE mode reads worker-local files. If you bake flags into the image,
the values are effectively fixed for that running job. To apply flag changes, publish a new image/template
or restart with updated worker-local files.
