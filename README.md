# OpenFeature FILE Mode Monorepo (Java 11+)

This repo is now a Spring Boot monorepo example with:
- one shared OpenFeature module
- two independent services
- one Maven Dataflow Flex Template example

FILE mode is fully offline and does not require a running flagd daemon.

## Monorepo Layout

```text
shared/openfeature-config/      # shared @Configuration + Client bean
service-a/                      # Spring Boot app + service-a flags + tests
service-b/                      # Spring Boot app + service-b flags
helm/service-a/                 # service-a chart (own ConfigMap)
helm/service-b/                 # service-b chart (own ConfigMap)
maven-dataflow-example/         # Maven + Dataflow Flex Template example
```

## Shared Module

Shared wiring lives in:
- `shared/openfeature-config/src/main/java/com/example/shared/openfeature/config/OpenFeatureConfig.java`

Both services import `project(":shared:openfeature-config")`.
The shared module provides provider wiring + `Client` bean only. Each service evaluates its own flags with its own result model.

## Run Services Locally

Run from repository root.

Service A:

```bash
APP_ENV=dev gradle :service-a:bootRun
```

Service B:

```bash
APP_ENV=dev gradle :service-b:bootRun
```

Endpoints:
- `http://localhost:8081/api/flags` (service-a)
- `http://localhost:8082/api/flags` (service-b)

Query with environment override:

```bash
curl "http://localhost:8081/api/flags?environment=staging"
curl "http://localhost:8082/api/flags?environment=prod"
```

## Per-Service Flag Ownership

Each service has its own flag files:
- `service-a/flags/flags-dev.json`
- `service-a/flags/flags-staging.json`
- `service-a/flags/flags-prod.json`
- `service-a/flags/flags-targeting.json`
- `service-b/flags/flags-dev.json`
- `service-b/flags/flags-staging.json`
- `service-b/flags/flags-prod.json`
- `service-b/flags/flags-targeting.json`

By default:
- service-a checks `flags`, then `service-a/flags`, then `/etc/flags`
- service-b checks `flags`, then `service-b/flags`, then `/etc/flags`

You can force an explicit file in any service:

```bash
FLAGD_OFFLINE_FLAG_SOURCE_PATH=/absolute/path/to/flags-prod.json gradle :service-a:bootRun
```

For CI and `java -jar`, use explicit path whenever possible:

```bash
FLAGD_OFFLINE_FLAG_SOURCE_PATH=/workspace/service-a/flags/flags-dev.json java -jar service-a.jar
```

Targeting mode example:

```bash
FLAG_MODE=targeting APP_ENV=prod gradle :service-a:bootRun
```

## Integration Test

Both services include integration tests for initial read + hot reload.

```bash
gradle :service-a:test --tests com.example.servicea.FeatureApiIntegrationTest
gradle :service-b:test --tests com.example.serviceb.FeatureApiIntegrationTest
```

## Validation And Drift Guard

Run repository flag validation:

```bash
./scripts/validate-flags.sh
```

Or through Gradle:

```bash
gradle validateFlags
```

Validation checks:
- JSON structure and required flag fields
- env key consistency (`dev`, `staging`, `prod`)
- Java-referenced flag keys exist in env files
- Helm/service flag drift detection

To resync Helm flag copies from service-owned flags:

```bash
./scripts/sync-helm-flags.sh
```

## Helm (One ConfigMap Per Service)

Service A:

```bash
helm upgrade --install service-a ./helm/service-a -f ./helm/service-a/values-dev.yaml
```

Service B:

```bash
helm upgrade --install service-b ./helm/service-b -f ./helm/service-b/values-dev.yaml
```

Each chart mounts only its own `flags-<env>.json` into `/etc/flags`.
To avoid drift while keeping chart packaging simple, this repo treats `service-*/flags` as source-of-truth and enforces sync via `validateFlags`.

## Dataflow Flex Template

The Dataflow module is unchanged in layout and available at:
- `maven-dataflow-example`

See:
- `maven-dataflow-example/README.md`
- `maven-dataflow-example/flex-template/Dockerfile`
- `maven-dataflow-example/flex-template/metadata.json`

For Flex runtime, FILE mode must use a worker-local path (not `gs://`), for example:
- `--flagFilePath=/opt/flags/flags-prod.json`
