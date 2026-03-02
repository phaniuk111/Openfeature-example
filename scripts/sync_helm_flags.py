#!/usr/bin/env python3
from pathlib import Path
import shutil


SERVICES = ("service-a", "service-b")
ENVS = ("dev", "staging", "prod")


def main():
    repo_root = Path(__file__).resolve().parents[1]
    for service in SERVICES:
        service_flag_dir = repo_root / service / "flags"
        helm_flag_dir = repo_root / "helm" / service / "flags"
        helm_flag_dir.mkdir(parents=True, exist_ok=True)

        for env in ENVS:
            src = service_flag_dir / f"flags-{env}.json"
            dst = helm_flag_dir / f"flags-{env}.json"
            shutil.copyfile(src, dst)
            print(f"Synced {src} -> {dst}")


if __name__ == "__main__":
    main()
