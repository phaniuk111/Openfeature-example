# Senior Engineer Assessment

**OpenFeature + flagd FILE Mode — Feature Flag Framework Proposal** | Reviewed March 2026

---

## Verdict: APPROVE with conditions

> This framework is **ready to propose to the team**. The architecture is sound, follows CNCF-backed standards (OpenFeature is a graduated project), avoids vendor lock-in, and covers both Spring Boot microservices and Dataflow batch pipelines. There are a handful of gaps that should be closed before production rollout — none are blockers, all are addressable in a single sprint. See Section 7 for the pre-production checklist.

---

## Scoreboard

| Dimension | Score | Notes |
|-----------|-------|-------|
| Architecture | **9/10** | Clean separation, vendor-neutral |
| Code Quality | **8/10** | Immutable models, proper DI |
| Test Coverage | **6/10** | Service-A solid; B + Dataflow missing |
| Production Readiness | **7/10** | Good bones, needs observability |
| DevEx / Onboarding | **8/10** | Simple mental model, few moving parts |
| Scalability | **8/10** | Per-service flags, monorepo proven |

---

## 1. What the framework does

A **zero-infrastructure feature flag system** using OpenFeature SDK + flagd provider in FILE mode. Flags live as JSON files in the repo, are mounted via Kubernetes ConfigMaps, and are evaluated in-process — no flag server, no network calls, no third-party SaaS.

It supports three evaluation modes: **per-environment** (static file per env), **API/dynamic** (context passed at runtime), and **targeting** (JsonLogic rules evaluated against user/env context).

---

## 2. Architecture assessment

### Repository structure

```
openfeature-monorepo-example/
├── shared/openfeature-config/       ← Provider wiring + Client bean (shared library)
│   └── OpenFeatureConfig.java
├── service-a/                       ← Spring Boot service on :8081
│   ├── model/ServiceAFeatureResult.java
│   ├── service/ServiceAFeatureEvaluationService.java
│   ├── web/FeatureController.java
│   ├── flags/  (dev, staging, prod JSON)
│   └── FeatureApiIntegrationTest.java
├── service-b/                       ← Spring Boot service on :8082
│   ├── model/ServiceBFeatureResult.java
│   ├── service/ServiceBFeatureEvaluationService.java
│   ├── web/FeatureController.java
│   └── flags/  (dev, staging, prod JSON)
├── maven-dataflow-example/          ← Dataflow Flex Template (Maven)
│   ├── FlexTemplateOpenFeaturePipeline.java
│   ├── DataflowFlagEvaluationExample.java
│   ├── flex-template/ (Dockerfile, metadata.json)
│   └── flags/  (dev, staging, prod JSON)
├── helm/
│   ├── service-a/  (Chart, values, ConfigMap, Deployment, Service)
│   └── service-b/  (same structure)
├── settings.gradle.kts
└── build.gradle.kts
```

### Strengths

- **Vendor-neutral** — OpenFeature is CNCF-graduated. Swap flagd for LaunchDarkly, Split, or CloudBees by changing one provider bean; zero application code changes.
- **Zero-infra** — FILE mode means no flag server to operate, no network latency, no availability risk. Flags evaluated in-process at microsecond speed.
- **GitOps-friendly** — flag files are in the repo, reviewed via PR, versioned in git. Full audit trail by default.
- **Hot-reload** — flagd's file-watcher picks up ConfigMap changes without pod restart. Proven in integration test.
- **Monorepo-correct** — shared module only provides wiring; each service owns its own model, service, and flag definitions. No shared-model coupling.
- **Multi-path resolution** — `FLAG_BASE_PATH` / `FLAG_BASE_PATHS` / explicit file precedence chain works across local dev, CI, and K8s.
- **Dataflow coverage** — DoFn lifecycle handled correctly with transient fields, @Setup/@Teardown, AtomicInteger instance tracking, and gs:// guard.

### Gaps to address

- **No service-b tests** — zero integration or unit tests for service-b. Copy service-a's pattern.
- **No Dataflow tests** — FlexTemplateOpenFeaturePipeline has no test coverage.
- **No flag evaluation logging** — no structured audit trail of which variant was returned for which context. Essential for debugging and compliance.
- **Duplicate flag files** — `service-a/flags/` and `helm/service-a/flags/` are identical copies. Single source of truth needed.
- **No flag schema validation in CI** — a typo in a flag name or malformed JSON deploys silently.
- **No targeting mode examples** — `flags-targeting.json` doesn't exist anywhere in the repo despite code supporting it.

---

## 3. Code quality deep dive

| File | Rating | Notes |
|------|--------|-------|
| `OpenFeatureConfig.java` | **SOLID** | Multi-path resolution is well-engineered. `destroyMethod="shutdown"` correct. Error messages include all attempted paths — excellent debuggability. Minor: could use `StringUtils.hasText()` from Spring instead of custom helper. |
| `ServiceAFeatureEvaluationService` | **SOLID** | Clean constructor injection, immutable context, all 4 flags evaluated with safe defaults. Minor: no try/catch around individual flag calls — one SDK failure kills the entire evaluation. |
| `ServiceAFeatureResult` | **SOLID** | All-final fields, proper boolean `is*` getters. Missing: `toString()` for debugging, and could be a Java record if targeting 16+. |
| `FeatureController (A)` | **SOLID** | Simple delegation to service. `LinkedHashMap` preserves JSON field order — nice touch. Missing: no `@ResponseStatus` or error handler for evaluation failures. |
| `FeatureApiIntegrationTest` | **SOLID** | Tests both initial load AND hot-reload with Awaitility polling. `@DynamicPropertySource` with temp directory is best practice. `writeFlags()` parameterized with all 4 flags. Minor: `FileTime.from(Instant.now().plusMillis(50))` is a workaround for inotify; should be documented. |
| `FlexTemplateOpenFeaturePipeline` | **SOLID** | All 5 previously identified bugs fixed. `transient` fields, `@Teardown` with AtomicInteger guard, gs:// rejection, `waitUntilFinish()`, try/catch in setup. Minor: `ACTIVE_INSTANCE_COUNT` uses AtomicInteger but OpenFeatureAPI is a JVM singleton — in a multi-threaded Beam worker this is safe but worth documenting the assumption. |
| `ServiceBFeatureEvaluationService` | **SOLID** | Mirrors service-a pattern correctly. Has its own flag (`recommendations-engine`) instead of `checkout-v2`. Same minor gaps as service-a. |
| `Helm charts (both)` | **OK** | Functional but minimal. Missing: readiness/liveness probes, resource limits, pod disruption budget, annotations for ConfigMap hash (to trigger rolling restart on flag change). `replicas: 1` hardcoded — should use values. |
| `build.gradle.kts (root)` | **SOLID** | `apply false` pattern correct for monorepo. `tasks.withType<Test>` is cosmetically outside the `plugins.withType<JavaPlugin>` guard — harmless but cleaner inside. Java 11 toolchain appropriate for Beam 2.57 compatibility. |

---

## 4. Risk register

### HIGH — Flag file drift between repo and Helm

`service-a/flags/` and `helm/service-a/flags/` are separate copies of the same data. If a dev edits one and forgets the other, environments diverge silently. **Fix:** symlink Helm flags to the service's flags directory, or use a CI check that diffs them.

### HIGH — No flag schema validation in CI

A typo in a flag key (e.g., `"new_ui"` instead of `"new-ui"`) will not break the build. The SDK will silently return the default value. This is feature flags' biggest operational risk. **Fix:** add a CI step that validates every `flags-*.json` against the flagd schema and checks that all flag keys referenced in Java code exist in every env file.

### MEDIUM — No evaluation-time observability

When a flag returns an unexpected value in production, there's no log or metric showing what variant was evaluated, with what context, at what time. **Fix:** add a global OpenFeature hook that logs every evaluation (flag key, variant, context hash, latency).

### MEDIUM — Single-point evaluation failure

If one flag evaluation throws (e.g., type mismatch), the entire `evaluate()` call fails and the controller returns 500. **Fix:** wrap each `client.get*Value()` in its own try/catch, log the failure, and use the default.

### MEDIUM — Helm lacks production hardening

No readiness/liveness probes, no resource requests/limits, no PDB, no ConfigMap hash annotation (so a flag-only change won't trigger a rolling restart of pods). These are standard K8s production requirements.

### LOW — No targeting-mode examples

The code supports `flags-targeting.json` with JsonLogic rules, but no example file exists. Team members won't know how to use this mode. **Fix:** add at least one reference targeting file with a documented rule.

---

## 5. How it compares to alternatives

| Criterion | This framework (flagd FILE) | LaunchDarkly / Split SaaS | Unleash (self-hosted) | Spring Cloud Config |
|-----------|----------------------------|--------------------------|----------------------|-------------------|
| Vendor lock-in | **None** — CNCF standard | High | Moderate | Spring ecosystem |
| Infrastructure | **Zero** — in-process | SaaS managed | Server + DB required | Config server required |
| Evaluation latency | **Microseconds** | ~10-50ms (cached) | ~10-30ms | At startup only |
| Real-time targeting | JsonLogic rules | Full UI | Full UI | None |
| GitOps / audit trail | **Native** — flags in repo | Separate audit log | Separate audit log | **Native** |
| Cost at 10 services | **$0** | $$$ per seat/flag | $$ (infra) | $ (infra) |
| Dataflow / Beam support | **Proven here** | Custom work | No native support | N/A |
| Migration to SaaS later | **Swap one bean** | N/A | SDK swap | Full rewrite |

---

## 6. Team proposal talking points

### Why propose this now

1. **Standards-based.** OpenFeature is the only CNCF-graduated feature flag standard. Adopting it now means any future provider migration (SaaS or self-hosted) requires changing one Spring bean, not application code.

2. **Zero operational cost.** FILE mode adds no infrastructure, no network dependency, no availability risk. The flag evaluation is literally a hashmap lookup in JVM memory.

3. **GitOps-native.** Flag changes go through the same PR review process as code. No separate flag management UI to learn, no separate access control system to manage.

4. **Proven across both runtimes.** This framework works identically in Spring Boot microservices and Dataflow Flex Template batch pipelines, which means one pattern for the whole team.

5. **Escape hatch built in.** If the team outgrows FILE mode and needs real-time targeting at scale, swapping to flagd gRPC mode or LaunchDarkly is a one-line provider change.

### What to be transparent about

1. **No UI.** There's no dashboard for PMs to toggle flags. Changes require a PR. For teams that want non-engineering flag control, this is a limitation. Counter-argument: for most teams, PR-based flag changes are actually safer.

2. **Deploy to activate.** In per-env mode, a flag change requires a ConfigMap update (K8s rolling update or manual `kubectl apply`). It's not instant like a SaaS toggle. Hot-reload mitigates this — the pod picks up ConfigMap changes within seconds — but it's still a deploy, not a button click.

3. **Flag drift risk.** Multiple copies of flag files exist today. This needs a CI guardrail before team adoption.

---

## 7. Pre-production checklist

Ordered by priority. Items marked **MUST** are blockers; **SHOULD** are strongly recommended; **NICE** can be deferred.

| # | Item | Priority | Effort | Detail |
|---|------|----------|--------|--------|
| 1 | Eliminate duplicate flag files | **MUST** | ~1h | Symlink `helm/service-a/flags/` → `service-a/flags/` (or single-source via Kustomize overlay). One source of truth. |
| 2 | CI flag schema validation | **MUST** | ~2h | Script that: (a) validates every `flags-*.json` against flagd schema; (b) checks all flag keys in Java code exist in every env file; (c) fails the build on mismatch. |
| 3 | Add OpenFeature evaluation hook for logging | **MUST** | ~2h | Global hook that logs: flag key, returned variant, context summary, evaluation latency. Essential for production debugging. |
| 4 | Service-b integration tests | **SHOULD** | ~2h | Copy service-a's `FeatureApiIntegrationTest` pattern. Include `recommendations-engine` flag. |
| 5 | Wrap individual flag evals in try/catch | **SHOULD** | ~1h | In both evaluation services, catch per-flag exceptions so one broken flag doesn't crash the endpoint. |
| 6 | Helm production hardening | **SHOULD** | ~3h | Add: readiness/liveness probes, resource requests/limits, PDB, ConfigMap hash annotation (`checksum/flags`), replicas from values. |
| 7 | Add a `flags-targeting.json` example | **SHOULD** | ~1h | Demonstrates JsonLogic rules. Without it, the targeting mode is undiscoverable. |
| 8 | Metrics (Micrometer) for flag evaluations | **NICE** | ~2h | Counter per flag key + variant. Enables Grafana dashboards showing flag distribution in real-time. |
| 9 | Dataflow pipeline unit test | **NICE** | ~3h | Use `TestPipeline` with a temp flag file to validate the EvaluateFlagsFn DoFn end-to-end. |
| 10 | `toString()` on FeatureResult models | **NICE** | ~15m | Helps with log debugging. Consider Java records if Java 16+ is feasible. |

---

## 8. Final recommendation

> **Propose it. Ship items 1–3 first.**
>
> The architecture is clean, the technology choice is sound (CNCF-backed, vendor-neutral, zero-infra), and the codebase demonstrates all the hard problems are solved — monorepo structure, multi-path flag resolution, hot-reload, Dataflow lifecycle management. The remaining gaps are all "polish and harden" work, not architectural concerns. Close items 1–3 (flag dedup, CI validation, eval logging) before merging to main, then tackle items 4–7 in the same sprint. This is a framework the team can adopt confidently.

---

*Assessment by senior engineering review | March 2026 | Codebase: openfeature-monorepo-example*
