---
name: snyk-cli-provisioner
description: >
  Checks whether the Snyk CLI is installed and authenticated. Installs via
  npm (globally) on macOS or Linux if missing. Guides the engineer through
  browser-based OAuth authentication if not authenticated. Runs
  snyk test --json and saves the full dependency vulnerability report to
  snyk-report.json at the repo root. Prints a human-readable severity
  summary — never logs the SNYK_TOKEN or any credential value.
trigger: "Run the snyk-cli-provisioner skill"
tags:
  - security
  - snyk
  - dependencies
  - vulnerabilities
  - devSecOps
---

# 🐛 Snyk CLI Provisioner Skill

## ⚡ Activation Command

```
Run the snyk-cli-provisioner skill
```

---

## 📌 Purpose

This skill ensures the **Snyk CLI is installed and authenticated**, then runs a
full dependency vulnerability scan (`snyk test --json`) against this repository
and saves the structured report to `snyk-report.json` at the repo root.

It covers the complete provisioning lifecycle:
- OS detection
- Snyk CLI install check → install via `npm` if missing
- Authentication check → browser-based OAuth flow if not authenticated
- Scan execution with JSON output
- Report protection (gitignore)
- Human-readable severity summary
- Remediation guidance

---

## 🛡️ Security Constraints (Always Enforced)

1. **MUST NOT request, log, print, or store `SNYK_TOKEN`** or any credential
   value — authentication is always done via the official `snyk auth` OAuth
   flow or environment variable injection, never by hardcoding.
2. **MUST NOT suggest** storing `SNYK_TOKEN` in source code, `.env` files
   committed to git, or any file tracked by version control.
3. **`snyk-report.json` MUST be added to `.gitignore`** — it contains full
   vulnerability details that are sensitive operational data.
4. This skill is **read-only with respect to source files** — it does not
   modify `pom.xml` or any dependency files.
5. All remediation decisions (upgrade, patch, ignore) must be made by the
   engineer, not auto-applied by this skill.
6. **MUST NOT disable TLS, certificate validation, or any Snyk security
   control** as a workaround.

---

## 🔀 Execution Flow

### STEP 1 — Detect the operating system

Run:
```bash
uname -s
```

- `Darwin` → macOS — use `npm install --global` (no `sudo` needed with nvm/Homebrew node)
- `Linux`  → Linux — use `npm install --global` (prefix with `sudo` if needed)
- Any other OS → stop and report:
  ```
  ❌ Unsupported OS. Please install Snyk CLI manually:
     https://docs.snyk.io/snyk-cli/install-or-update-the-snyk-cli
  Then re-run this skill.
  ```

---

### STEP 2 — Check if Snyk CLI is installed

Run:
```bash
which snyk 2>/dev/null && snyk --version || echo "NOT_INSTALLED"
```

- Found → print the version, skip to **STEP 4**.
- `NOT_INSTALLED` → proceed to **STEP 3**.

---

### STEP 3 — Install Snyk CLI via npm

Run:
```bash
npm install --global snyk 2>&1
```

If `npm` itself is not found, stop and report:
```
❌ npm is not installed. Install Node.js (LTS) first:
   https://nodejs.org/en/download
Then re-run this skill.
```

After install, verify:
```bash
snyk --version
```

If verification fails, stop and report:
```
❌ Snyk CLI installation failed.
   Install manually: https://docs.snyk.io/snyk-cli/install-or-update-the-snyk-cli
Then re-run this skill.
```

---

### STEP 4 — Check Snyk authentication

Run:
```bash
snyk whoami 2>&1
```

- Exit code **0** and username printed → authenticated. Skip to **STEP 6**.
- Any auth error (401 / `SNYK-0005`) → proceed to **STEP 5**.

---

### STEP 5 — Authenticate Snyk CLI

#### Option A — Environment variable (preferred for CI / non-interactive)

If `SNYK_TOKEN` is already set in the environment:
```bash
# The CLI picks up SNYK_TOKEN automatically — no explicit auth command needed.
# Verify it works:
snyk whoami 2>&1
```

If that succeeds → skip to **STEP 6**.

#### Option B — Interactive browser OAuth (local developer flow)

Instruct the engineer:
```
🔐 Authentication required. Running: snyk auth
   Your browser will open to complete the OAuth flow.
   Log in with your Snyk account and return here when done.
   IMPORTANT: Do NOT paste your SNYK_TOKEN into this chat or any source file.
```

Run:
```bash
snyk auth 2>&1
```

Wait for confirmation, then verify:
```bash
snyk whoami 2>&1
```

If still not authenticated after the OAuth flow, stop and report:
```
❌ Snyk authentication failed.
   Options:
   1. Set SNYK_TOKEN as an environment variable (never in source code):
        export SNYK_TOKEN="<your-token>"   # in your shell profile only
   2. Re-run: snyk auth
   3. Check your Snyk account at: https://app.snyk.io/account
```

---

### STEP 6 — Run the Snyk dependency scan

Run from the **repository root**:

```bash
snyk test \
  --json \
  --all-projects \
  2>&1 | tee snyk-report.json; echo "SNYK_EXIT:$?"
```

> **Exit code notes:**
> - `0` → no vulnerabilities found ✅
> - `1` → vulnerabilities found (expected — not a crash) ✅
> - `2` → Snyk CLI error (config, network, auth) ❌
> - `3` → no supported projects detected ❌
>
> Treat exit codes `0` and `1` as valid scan outcomes.
> Exit codes `2` and `3` are errors — report them and stop.

If exit code is `2` or `3`, report:
```
❌ snyk test failed with exit code <N>.
   Check: snyk test --debug for details.
   Common causes: missing pom.xml, unauthenticated CLI, network error.
```

---

### STEP 7 — Protect the report file

Ensure `snyk-report.json` is excluded from version control:

```bash
grep -q "snyk-report.json" .gitignore \
  || echo "snyk-report.json" >> .gitignore
```

Report:
```
✅ snyk-report.json added to .gitignore (or was already present).
```

---

### STEP 8 — Parse and summarise findings

Read `snyk-report.json`.

Snyk `--all-projects` outputs either a **single object** or an **array**.
Normalise to array before parsing:

```bash
jq 'if type == "array" then . else [.] end' snyk-report.json
```

#### If no vulnerabilities found:
```
✅ CLEAN SCAN — No vulnerabilities detected.
   Report saved to: snyk-report.json
```

#### If vulnerabilities found, print:

```
❌ VULNERABILITIES DETECTED
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 Full report saved to: snyk-report.json

 Severity Summary
 ┌────────────┬───────┐
 │ Severity   │ Count │
 ├────────────┼───────┤
 │ 🔴 Critical │  <N>  │
 │ 🟠 High     │  <N>  │
 │ 🟡 Medium   │  <N>  │
 │ 🔵 Low      │  <N>  │
 │ Total       │  <N>  │
 └────────────┴───────┘

 Top Findings (up to 10, Critical → High → Medium → Low)
 #  │ Severity  │ Package              │ Version  │ Fixed In    │ CVE / Title
────┼───────────┼──────────────────────┼──────────┼─────────────┼────────────────────
 1  │ 🔴 critical│ <pkg>               │ <ver>    │ <fix>       │ <title>
 ...
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
⚠️  DO NOT commit snyk-report.json — it is now in .gitignore.
⚠️  Review and remediate findings before merging to main.
```

**Rules for the findings table:**
- Show: severity, package name, installed version, fixed-in version (or
  `No fix available`), and CVE ID / title.
- **Never** include authentication headers, tokens, or internal Snyk
  organisation identifiers from the JSON.
- Truncate package names longer than 22 characters with `…`.
- Sort: Critical → High → Medium → Low, then alphabetically by package.
- Show at most **10 rows** — note total count above the table.

---

### STEP 9 — Remediation guidance

```
🔧 Remediation Steps
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
For EACH vulnerability:

 1. UPGRADE the vulnerable package to the "Fixed In" version in pom.xml.
    For BOM-managed deps, upgrade the parent BOM version instead.

 2. If no fix is available, evaluate:
    a. Snyk ignore (temporary, with justification):
         snyk ignore --id=<CVE-ID> --reason="<justification>" --expiry=<date>
    b. Remove the dependency if it is unused.
    c. Isolate in a separate module with restricted scope.

 3. RE-BUILD to confirm no compilation errors after upgrades:
      ./mvnw -B clean compile --no-transfer-progress

 4. RE-RUN this skill to confirm vulnerability count drops to zero.

 5. NEVER disable Snyk security checks or suppress findings without
    a documented justification and an expiry date.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## 📋 PR Review Checklist

When this skill is used before raising a PR, confirm:

- [ ] `snyk-report.json` is listed in `.gitignore`
- [ ] `SNYK_TOKEN` was NOT hardcoded anywhere — used via env var or `snyk auth` only
- [ ] No Critical or High vulnerabilities remain, OR each has a documented
      ignore with justification and expiry date in `.snyk`
- [ ] `pom.xml` changes compile cleanly: `./mvnw -B clean compile`
- [ ] Re-scan confirms clean (or accepted) result before merge

---

## 🚫 What This Skill Will NOT Do

| Action | Reason |
|--------|--------|
| Print or store `SNYK_TOKEN` | Security — tokens are secrets, never logged |
| Auto-upgrade `pom.xml` dependencies | Engineer must review breaking changes |
| Suppress findings without justification | Violates security baseline |
| Disable TLS or certificate checks | Hard security prohibition |
| Commit `snyk-report.json` | Contains sensitive vulnerability metadata |
| Store credentials in any tracked file | Secrets must stay in env vars / secret managers |
