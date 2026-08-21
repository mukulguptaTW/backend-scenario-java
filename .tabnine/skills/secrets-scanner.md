---
name: secrets-scanner
description: >
  Checks whether gitleaks is installed (installs via Homebrew on macOS or
  apt-get on Linux if missing), then scans the entire repository for committed
  secrets using gitleaks. Saves the full findings report to gitleaks-report.json
  at the repo root. Prints a human-readable summary and flags any detected
  secrets with file path, line number, rule ID, and commit SHA.
trigger: "Use the secrets-scanner skill to scan this repo"
tags:
  - security
  - secrets
  - gitleaks
  - devSecOps
---

# 🔐 Secrets Scanner Skill

## ⚡ Activation Command

```
Use the secrets-scanner skill to scan this repo
```

---

## 📌 Purpose

This skill scans the repository for **hardcoded secrets, credentials, API keys,
tokens, and other sensitive values** that have been accidentally committed to
source code or version history.

It uses [gitleaks](https://github.com/gitleaks/gitleaks) — an open-source,
MIT-licensed secret scanner — and saves the full structured report to
`gitleaks-report.json` at the repository root.

---

## 🛡️ Security Constraints (Always Enforced)

1. **Never print actual secret values** — only report file, line, rule ID, and
   a redacted match preview.
2. **Never suggest disabling or bypassing gitleaks rules** as a workaround.
3. **Never log, store, or echo the discovered secret values** in summaries,
   comments, or output.
4. **gitleaks-report.json must be added to `.gitignore`** — it contains
   structured findings that could reveal secret metadata.
5. This skill is **read-only** — it does not modify source files.
6. All remediation decisions (revoke, rotate, move to env vars) must be made
   by the engineer, not auto-applied by this skill.

---

## 🔀 Execution Flow

### STEP 1 — Detect the operating system

Run:
```bash
uname -s
```

- If output is `Darwin` → use **Homebrew** to install gitleaks.
- If output is `Linux` → use **apt-get** to install gitleaks.
- For any other OS, stop and report:
  ```
  ❌ Unsupported OS. Please install gitleaks manually:
     https://github.com/gitleaks/gitleaks#installing
  Then re-run this skill.
  ```

---

### STEP 2 — Check if gitleaks is installed

Run:
```bash
which gitleaks 2>/dev/null && gitleaks version || echo "NOT_INSTALLED"
```

- If gitleaks is found → print the version and skip to **STEP 4**.
- If `NOT_INSTALLED` → proceed to **STEP 3**.

---

### STEP 3 — Install gitleaks

#### macOS (Homebrew)
```bash
brew install gitleaks
```

#### Linux (apt-get)
```bash
sudo apt-get update -qq && sudo apt-get install -y gitleaks
```

After installation, verify with:
```bash
gitleaks version
```

If installation fails, stop and report:
```
❌ gitleaks installation failed.
   Please install it manually: https://github.com/gitleaks/gitleaks#installing
   Then re-run this skill.
```

---

### STEP 4 — Run the secrets scan

Run the following command from the **repository root**:

```bash
gitleaks detect \
  --source . \
  --report-format json \
  --report-path gitleaks-report.json \
  --redact \
  --no-git \
  --verbose \
  2>&1
```

**Flag explanations:**
| Flag | Purpose |
|------|---------|
| `--source .` | Scan the current directory (entire repo) |
| `--report-format json` | Machine-readable output for CI integration |
| `--report-path gitleaks-report.json` | Save findings to repo root |
| `--redact` | Redacts the actual secret value in the report — only metadata is stored |
| `--no-git` | Scans working-tree files directly (catches uncommitted changes too) |
| `--verbose` | Prints findings to stdout in real time |

> **Note:** gitleaks exits with code `1` when secrets are found and `0` when clean.
> The skill treats both as valid outcomes — only a crash (exit code > 1) is an error.

---

### STEP 5 — Protect the report file

Ensure `gitleaks-report.json` is excluded from version control:

```bash
grep -q "gitleaks-report.json" .gitignore \
  || echo "gitleaks-report.json" >> .gitignore
```

Report to the engineer:
```
✅ gitleaks-report.json added to .gitignore (or was already present).
```

---

### STEP 6 — Parse and summarise findings

Read `gitleaks-report.json` and produce the summary below.

If the file is empty or `[]`:
```
✅ CLEAN SCAN — No secrets detected.
   Report saved to: gitleaks-report.json
```

If findings exist, print:

```
❌ SECRETS DETECTED — <N> finding(s)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 Full report saved to: gitleaks-report.json

 #  │ Rule ID          │ File                                  │ Line │ Commit
────┼──────────────────┼───────────────────────────────────────┼──────┼─────────
 1  │ <ruleID>         │ <file>                                │ <L>  │ <sha>
 2  │ <ruleID>         │ <file>                                │ <L>  │ <sha>
 ...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️  DO NOT commit gitleaks-report.json — it is now in .gitignore.
⚠️  Rotate / revoke any real secrets immediately.
⚠️  Never print or log the actual secret values.
```

**Rules for the summary table:**
- Show **Rule ID**, **File path** (relative to repo root), **Line number**, and
  **Commit SHA** (or `working-tree` if `--no-git` was used).
- **Never** include the `Secret`, `Match`, or `RawValue` fields from the JSON —
  these contain actual credential values.
- Truncate file paths longer than 40 characters with `…` on the left.
- Sort by file path ascending.

---

### STEP 7 — Remediation guidance

After the summary table, print the following remediation checklist:

```
🔧 Remediation Steps
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
For EACH detected secret:

 1. REVOKE / ROTATE the credential immediately with the issuing service.
    Do this BEFORE pushing any fix — the secret is already in git history.

 2. REMOVE the hardcoded value from the source file.
    Replace with an environment variable reference, e.g.:
      spring.datasource.password=${DB_PASSWORD}

 3. PURGE from git history (if it was ever committed):
      git filter-repo --path <file> --invert-paths   # nuclear option
    OR use BFG Repo Cleaner for a targeted approach.

 4. FORCE-PUSH the cleaned history and notify all collaborators to re-clone.

 5. ADD the secret pattern to a .gitleaks.toml allowlist ONLY if it is a
    known test/fake value — never to suppress real credentials.

 6. RE-RUN this skill after remediation to confirm a clean scan.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 📋 PR Review Checklist

When this skill is used before raising a PR, confirm the following:

- [ ] `gitleaks-report.json` is listed in `.gitignore`
- [ ] No secrets detected, OR all findings are confirmed test/fake values with
      a documented justification in `.gitleaks.toml`
- [ ] All real credentials have been revoked and rotated
- [ ] Secrets replaced with environment variable references
- [ ] Git history purged if any real secret was ever committed
- [ ] Re-scan confirms clean result before merge

---

## 🚫 What This Skill Will NOT Do

| Action | Reason |
|--------|--------|
| Print actual secret values | Security — avoids leaking secrets into AI context or logs |
| Auto-remove secrets from files | Engineer must review and rotate first |
| Purge git history automatically | Destructive — requires explicit engineer approval |
| Disable or bypass gitleaks rules | Violates security baseline |
| Commit `gitleaks-report.json` | File may contain secret metadata |
