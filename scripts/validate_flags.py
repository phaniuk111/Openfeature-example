#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Set


RE_FLAG_CALL = re.compile(
    r'get(?:Boolean|Integer|String|Double|Object)Value\(\s*"([^"]+)"'
)

SERVICES = ("service-a", "service-b")
ENVS = ("dev", "staging", "prod")


def load_json(path: Path, errors: List[str]) -> Optional[dict]:
    if not path.exists():
        errors.append(f"Missing file: {path}")
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as ex:
        errors.append(f"Invalid JSON: {path}: {ex}")
        return None


def validate_flag_file(path: Path, payload: dict, errors: List[str]) -> None:
    if not isinstance(payload, dict):
        errors.append(f"{path}: top-level JSON must be an object")
        return

    flags = payload.get("flags")
    if not isinstance(flags, dict):
        errors.append(f"{path}: missing or invalid 'flags' object")
        return

    for flag_key, flag_def in flags.items():
        if not isinstance(flag_def, dict):
            errors.append(f"{path}: flag '{flag_key}' definition must be an object")
            continue

        state = flag_def.get("state")
        variants = flag_def.get("variants")
        default_variant = flag_def.get("defaultVariant")

        if not isinstance(state, str) or not state:
            errors.append(f"{path}: flag '{flag_key}' must contain non-empty 'state'")
        if not isinstance(variants, dict) or not variants:
            errors.append(f"{path}: flag '{flag_key}' must contain non-empty 'variants' object")
            continue
        if not isinstance(default_variant, str) or not default_variant:
            errors.append(f"{path}: flag '{flag_key}' must contain non-empty 'defaultVariant'")
            continue
        if default_variant not in variants:
            errors.append(
                f"{path}: flag '{flag_key}' defaultVariant '{default_variant}' is not in variants"
            )


def extract_flag_keys_from_service(service_dir: Path) -> Set[str]:
    keys: Set[str] = set()
    java_root = service_dir / "src" / "main" / "java"
    for java_file in java_root.rglob("*.java"):
        text = java_file.read_text(encoding="utf-8")
        keys.update(RE_FLAG_CALL.findall(text))
    return keys


def compare_json_equivalence(path_a: Path, path_b: Path, errors: List[str]) -> None:
    payload_a = load_json(path_a, errors)
    payload_b = load_json(path_b, errors)
    if payload_a is None or payload_b is None:
        return
    if payload_a != payload_b:
        errors.append(
            "Flag drift detected: "
            f"{path_a} and {path_b} differ. Run sync or update both intentionally."
        )


def main() -> int:
    repo_root = Path(__file__).resolve().parents[1]
    errors: List[str] = []

    for service in SERVICES:
        service_dir = repo_root / service
        flag_dir = service_dir / "flags"
        env_payloads: Dict[str, dict] = {}

        for env in ENVS:
            path = flag_dir / f"flags-{env}.json"
            payload = load_json(path, errors)
            if payload is not None:
                validate_flag_file(path, payload, errors)
                env_payloads[env] = payload

        if len(env_payloads) == len(ENVS):
            env_keys = {
                env: set(payload["flags"].keys()) for env, payload in env_payloads.items()
            }
            baseline_env = ENVS[0]
            baseline_keys = env_keys[baseline_env]
            for env in ENVS[1:]:
                if env_keys[env] != baseline_keys:
                    errors.append(
                        f"{service}: flag keys differ between {baseline_env} and {env}: "
                        f"{sorted(baseline_keys.symmetric_difference(env_keys[env]))}"
                    )

            referenced_keys = extract_flag_keys_from_service(service_dir)
            missing = sorted(referenced_keys - baseline_keys)
            if missing:
                errors.append(
                    f"{service}: code references missing flags in env files: {missing}"
                )

        targeting_path = flag_dir / "flags-targeting.json"
        if targeting_path.exists():
            targeting_payload = load_json(targeting_path, errors)
            if targeting_payload is not None:
                validate_flag_file(targeting_path, targeting_payload, errors)

        helm_flag_dir = repo_root / "helm" / service / "flags"
        for env in ENVS:
            compare_json_equivalence(
                flag_dir / f"flags-{env}.json",
                helm_flag_dir / f"flags-{env}.json",
                errors,
            )

    maven_flags = repo_root / "maven-dataflow-example" / "flags"
    maven_payloads: Dict[str, dict] = {}
    for env in ENVS:
        path = maven_flags / f"flags-{env}.json"
        payload = load_json(path, errors)
        if payload is not None:
            validate_flag_file(path, payload, errors)
            maven_payloads[env] = payload

    if len(maven_payloads) == len(ENVS):
        maven_keys = {
            env: set(payload["flags"].keys()) for env, payload in maven_payloads.items()
        }
        if not (maven_keys["dev"] == maven_keys["staging"] == maven_keys["prod"]):
            errors.append(
                "maven-dataflow-example: flag keys differ across dev/staging/prod files"
            )

    if errors:
        for err in errors:
            print(f"[ERROR] {err}")
        print(f"\nValidation failed with {len(errors)} issue(s).")
        return 1

    print("Flag validation passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
