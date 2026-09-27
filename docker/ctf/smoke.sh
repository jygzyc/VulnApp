#!/usr/bin/env bash
# Smoke test inside the CTF container: every adb probe must pass (12/12).
set -euo pipefail
python3 /ctf/harness/verify.py --all --serial "$(adb devices | awk 'NR==2{print $1}')"
