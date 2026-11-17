# AGENTS.md — Python project overlay

Apply these rules only to this Python repository, in addition to the global AGENTS.md.

## Environment and dependencies

- Inspect `pyproject.toml`, lockfiles, existing tooling, and supported Python version before changing setup.
- Use the repository's existing virtual environment/package manager. Do not install packages globally.
- Do not add or upgrade dependencies unless the task requires it; explain the reason when adding one.
- Prefer the standard library and existing dependencies.

## Code

- Follow the repository's typing, formatting, linting, async, logging, and exception conventions.
- Keep public APIs backwards-compatible unless the task explicitly requests a breaking change.
- Validate external input at boundaries; avoid broad `except Exception` unless it is deliberate and justified.
- Do not silently ignore failures or warnings that affect correctness.

## Verification

Discover commands from `pyproject.toml`, task runners, and CI. Prefer the narrowest configured check first, for example a focused test, then the repository's configured lint/typecheck/test commands as justified.

Do not assume `pytest`, `ruff`, `mypy`, or another tool is available unless the repository config/dependencies show it.
