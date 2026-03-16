# OpenFeature FILE Mode — Java Monorepo

A production-quality feature flag framework using the [OpenFeature](https://openfeature.dev/) SDK + flagd provider in **FILE mode**.

Flags live as JSON files in the repo, are mounted via Kubernetes ConfigMaps, and evaluated in-process — **no flag server, no network calls, no third-party SaaS**.

> **Verdict (senior review, March 2026):** Architecture approved. CNCF-graduated standard, vendor-neutral, zero-infra, proven across Spring Boot microservices and Dataflow Flex Template pipelines.

---

## Monorepo Layout

```text
shared/openfeature-config/      # Shared @Configuration + OpenFeature Client bean
service-a/                      # Spring Boot :8081 · 4 flags · integration tests
service-b/                      # Spring Boot :8082 · 4 flags · integration tests
helm/service-a/                 # Helm chart — mounts service-a ConfigMap
helm/service-b/                 # Helm chart — mounts service-b ConfigMap
maven-dataflow-example/         # Dataflow Flex Template (Maven)
scripts/                        # validate-flags.sh + validate_flags.py
```

---

## Evaluation Modes

| Mode | How to activate | Flag file used |
|------|----------------|----------------|
| **Per-env** (default) | `APP_ENV=dev\|staging\|prod` | `flags-<env>.json` |
| **Dynamic / API** | Pass `?environment=` + `?namespace=` at query time | `flags-<env>.json` (resolved per request) |
| **Targeting** | `FLAG_MODE=targeting` | `flags-targeting.json` (JsonLogic rules) |

---

## Shared Module

Provider wiring lives in one place:

```
shared/openfeature-config/src/main/java/com/example/shared/openfeature/config/OpenFeatureConfig.java
```

Both services declare `project(":shared:openfeature-config")` as a dependency. The shared module provides the `Client` bean only — each service owns its own result model, evaluation service, and flag files.

**Flag file resolution order** (first file found wins):

1. `FLAGD_OFFLINE_FLAG_SOURCE_PATH` (explicit override — recommended for CI and `java -jar`)
2. `FLAG_BASE_PATHS` (colon-separated list)
3. `FLAG_BASE_PATH` (single directory)
4. `service-<x>/flags/` (service-local default)
5. `/etc/flags/` (Kubernetes ConfigMap mount)

---

## Run Services Locally

```bash
# Service A on :8081
APP_ENV=dev ./gradlew :service-a:bootRun

# Service B on :8082
APP_ENV=dev ./gradlew :service-b:bootRun
```

**Endpoints:**
- `http://localhost:8081/api/flags`
- `http://localhost:8082/api/flags`

**Query with environment / namespace override:**

```bash
curl "http://localhost:8081/api/flags?environment=staging&namespace=staging"
curl "http://localhost:8081/api/flags?environment=dev&namespace=dev2"
curl "http://localhost:8082/api/flags?environment=prod&namespace=prod"
```

**Targeting mode:**

```bash
FLAG_MODE=targeting APP_ENV=prod ./gradlew :service-a:bootRun
```

**Explicit flag file (CI / `java -jar`):**

```bash
FLAGD_OFFLINE_FLAG_SOURCE_PATH=/workspace/service-a/flags/flags-dev.json java -jar service-a.jar
```

---

## Flag Files

Each service owns its flag files independently:

```
service-a/flags/
  flags-dev.json
  flags-staging.json
  flags-prod.json
  flags-targeting.json     ← JsonLogic targeting rules

service-b/flags/
  flags-dev.json
  flags-staging.json
  flags-prod.json
  flags-targeting.json     ← JsonLogic targeting rules
```

**Targeting file behaviour** (`flags-targeting.json`):

- Namespaces `dev`, `dev1`, `dev2` → dev variant
- `environment=staging` → staging variant
- `environment=prod` or `production` → prod variant
- All other inputs → `defaultVariant`

---

## Flag Registration — How It Works

> **You never touch `OpenFeatureConfig.java` to register a flag.**
> Any key you declare in a flag JSON file is automatically available to the SDK.

---

### How the provider loads flags

`OpenFeatureConfig.java` (in `shared/openfeature-config/`) sets up the provider **once at startup**:

```java
FlagdProvider provider = new FlagdProvider(
    FlagdOptions.builder()
        .resolverType(Config.Resolver.FILE)   // in-process FILE mode — no flagd daemon
        .offlineFlagSourcePath(flagFile)       // resolved path e.g. service-a/flags/flags-dev.json
        .deadline(500)                         // ms timeout for provider init
        .build());

OpenFeatureAPI.getInstance().setProviderAndWait(provider); // blocks until ready
```

The provider reads every `"flags"` key in that file and registers them all. **No further code change is needed to register new flags** — just add the key to the JSON.

---

### Which file gets loaded

The file path is resolved at startup by this precedence chain:

| Priority | Property / Env Var | Example value |
|--|--|--|
| 1 (highest) | `app.flags.explicit-file` / `FLAGD_OFFLINE_FLAG_SOURCE_PATH` | `/workspace/service-a/flags/flags-dev.json` |
| 2 | `app.flags.base-paths` (comma-separated dirs) | `service-a/flags,/etc/flags` |
| 3 | `app.flags.base-path` (single dir) | `service-a/flags` |
| 4 (default) | `flags/` in working directory | `flags/flags-dev.json` |

File name is chosen by `app.flags.mode`:
- `per-env` (default) → `flags-<APP_ENV>.json`
- `targeting` → `flags-targeting.json`

---

### flagd flag JSON schema

Every flag file must start with:

```json
{
  "$schema": "https://flagd.dev/schema/v0/flags.json",
  "flags": {
    "<flag-key>": {
      "state": "ENABLED",
      "variants": { ... },
      "defaultVariant": "<variant-name>",
      "targeting": { ... }   // optional — JsonLogic, omit for static flags
    }
  }
}
```

Supported flag types and their SDK methods:

| JSON variant value type | SDK call in Java |
|---|---|
| `true` / `false` | `client.getBooleanValue(key, defaultVal, ctx)` |
| number (`150`, `900`) | `client.getIntegerValue(key, defaultVal, ctx)` |
| string (`"Service A - dev"`) | `client.getStringValue(key, defaultVal, ctx)` |

> [!NOTE]
> Flag keys use `kebab-case` in JSON (e.g. `"dark-mode"`) and are accessed by that exact string in Java.
> The SDK returns the `defaultVariant` value — **not** the key name — when no targeting rule matches.

---

### Hot-reload

The provider polls the flag file every **5 seconds** for changes. When a ConfigMap update is applied in Kubernetes, the pod picks up new flag values within 5 seconds — **no pod restart needed**.

---

## How to Add a New Feature Flag

Adding a flag touches **5 files** in your service. The example below adds a boolean flag called `dark-mode` to `service-a`.

---

### Step 1 — Declare the flag in all three env files

Add the same key to `flags-dev.json`, `flags-staging.json`, and `flags-prod.json`. Variants and `defaultVariant` may differ per env.

**`service-a/flags/flags-dev.json`**
```json
{
  "$schema": "https://flagd.dev/schema/v0/flags.json",
  "flags": {
    "dark-mode": {
      "state": "ENABLED",
      "variants": { "on": true, "off": false },
      "defaultVariant": "on"
    }
  }
}
```

**`service-a/flags/flags-staging.json`**
```json
"dark-mode": {
  "state": "ENABLED",
  "variants": { "on": true, "off": false },
  "defaultVariant": "on"
}
```

**`service-a/flags/flags-prod.json`**
```json
"dark-mode": {
  "state": "ENABLED",
  "variants": { "on": true, "off": false },
  "defaultVariant": "off"
}
```

> [!IMPORTANT]
> The flag key **must be identical** across all three env files. A mismatch causes the CI validation step to fail.

---

### Step 2 — Add the flag to `flags-targeting.json` (if using targeting mode)

If you want JsonLogic rules to control this flag, add it to `service-a/flags/flags-targeting.json`:

```json
"dark-mode": {
  "state": "ENABLED",
  "variants": { "on": true, "off": false },
  "defaultVariant": "off",
  "targeting": {
    "if": [
      { "or": [
          { "==": [{ "var": "namespace" }, "dev"]  },
          { "==": [{ "var": "namespace" }, "dev1"] },
          { "==": [{ "var": "namespace" }, "dev2"] }
      ]},
      "on",
      { "==": [{ "var": "environment" }, "staging"] },
      "on",
      "off"
    ]
  }
}
```

Skip this step if you only need per-env static values.

---

### Step 3 — Add a field to the result model

In `ServiceAFeatureResult.java`, add a `final` field, constructor param, and getter:

```java
// field
private final boolean darkMode;

// constructor param
boolean darkMode

// constructor body
this.darkMode = darkMode;

// getter
public boolean isDarkMode() { return darkMode; }
```

---

### Step 4 — Evaluate the flag in the service

In `ServiceAFeatureEvaluationService.java`, call `safeBoolean()` (or `safeString()` / `safeInteger()`)
and pass the result into the constructor:

```java
boolean darkMode =
    safeBoolean(
        "dark-mode",   // must match the key in flags-*.json exactly
        false,         // safe default if the SDK throws or key is missing
        context,
        effectiveEnvironment,
        effectiveNamespace,
        userIdPresent);

return new ServiceAFeatureResult(
    effectiveEnvironment, effectiveNamespace,
    newUiEnabled, batchSize, banner, checkoutV2,
    darkMode);          // ← new field added at the end
```

| SDK method | Flag value type | Java return type |
|---|---|---|
| `safeBoolean(key, false, ...)` | `true` / `false` | `boolean` |
| `safeInteger(key, 0, ...)` | numeric | `Integer` |
| `safeString(key, "fallback", ...)` | string | `String` |

---

### Step 5 — Cover it in the integration test

In `FeatureApiIntegrationTest.java`, add `"dark-mode"` to the `writeFlags()` helper and assert the
returned JSON field:

```java
// in writeFlags()
flags.put("dark-mode", Map.of(
    "state", "ENABLED",
    "variants", Map.of("on", true, "off", false),
    "defaultVariant", "on"
));

// assertion
assertThat(result.get("darkMode")).isEqualTo(true);
```

---

### Step 6 — Validate and run

```bash
# Check all flag keys are consistent across env files and Java code
./gradlew validateFlags

# Run the integration test
./gradlew :service-a:test --tests com.example.servicea.FeatureApiIntegrationTest
```

Fix any drift the validator reports before opening a PR. The CI pipeline runs `validateFlags` automatically.

---

## Integration Tests

Both services include tests for initial flag read and hot-reload (Awaitility polling + `@DynamicPropertySource`):

```bash
./gradlew :service-a:test --tests com.example.servicea.FeatureApiIntegrationTest
./gradlew :service-b:test --tests com.example.serviceb.FeatureApiIntegrationTest
```

---

## Flag Validation (CI Guard)

Validates JSON structure, required fields, env-key consistency, and flag-key drift between Java sources and flag files:

```bash
# via shell script
./scripts/validate-flags.sh

# via Gradle task
./gradlew validateFlags
```

Validation checks:
- JSON is well-formed and matches the flagd schema
- All env files (`dev`, `staging`, `prod`) have the same flag keys
- Every flag key referenced in Java code exists in every env file
- Helm ConfigMap flag copies are in sync with `service-*/flags/` (source of truth)

---

## Helm Deployment

Each chart mounts only its own service's flag files into `/etc/flags`.

```bash
# Service A
helm upgrade --install service-a ./helm/service-a -f ./helm/service-a/values-dev.yaml

# Service B
helm upgrade --install service-b ./helm/service-b -f ./helm/service-b/values-dev.yaml
```

`service-*/flags/` is the **source of truth**. Helm ConfigMap copies must stay in sync — enforced by `validateFlags`.

---

## Dataflow Flex Template

See [`maven-dataflow-example/`](./maven-dataflow-example/) for the full Maven-based Dataflow module.

Key points:
- OpenFeature `Client` is a `transient` DoFn field, initialized in `@Setup` / torn down in `@Teardown`
- `gs://` paths are rejected at startup — flag files must be worker-local
- Use `--flagFilePath=/opt/flags/flags-prod.json` at Flex Template launch time

---

## Why This Stack

| Criterion | flagd FILE mode | SaaS (LaunchDarkly/Split) | Unleash |
|-----------|----------------|--------------------------|---------|
| Vendor lock-in | **None** (CNCF standard) | High | Moderate |
| Infrastructure | **Zero** (in-process) | SaaS managed | Server + DB |
| Evaluation latency | **Microseconds** | ~10–50 ms (cached) | ~10–30 ms |
| GitOps / audit trail | **Native** (flags in repo) | Separate audit log | Separate audit log |
| Cost | **$0** | $$$ | $$ |
| Dataflow / Beam | **Proven** | Custom work | None |
| Migrate to SaaS later | **Swap one bean** | N/A | SDK swap |

---

## Pre-Production Checklist

| Priority | Item |
|----------|------|
| **MUST** | CI flag schema validation (`validateFlags` in pipeline) |
| **MUST** | Global OpenFeature hook for evaluation logging (flag key, variant, context, latency) |
| **SHOULD** | Per-flag `try/catch` in evaluation services (one bad flag ≠ 500 response) |
| **SHOULD** | Helm production hardening (probes, resource limits, PDB, ConfigMap hash annotation) |
| **NICE** | Micrometer metrics counter per flag key + variant |
| **NICE** | Dataflow `TestPipeline` unit test for `EvaluateFlagsFn` DoFn |

---

*OpenFeature is a [CNCF graduated project](https://www.cncf.io/projects/openfeature/). [flagd](https://flagd.dev/) is its reference implementation.*
