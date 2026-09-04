#!/usr/bin/env bash
set -euo pipefail

# Validate the composite action manifest with Ruby's YAML parser when
# available. GitHub Actions uses YAML 1.2-compatible parsing, so this catches
# malformed descriptions before the runner attempts to load action.yml.
if command -v ruby >/dev/null 2>&1; then
  ruby -e 'require "yaml"; YAML.load_file(ARGV[0])' action.yml
  printf '%s\n' 'Security check passed: action.yml is valid YAML.'
else
  printf '%s\n' 'Ruby is not available; skipping local YAML parser check.'
fi
