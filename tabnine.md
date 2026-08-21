# 🤖 Tabnine CLI — Project Guide

> **Project:** Spring PetClinic REST (Java 21 / Spring Boot)  
> **CI:** GitHub Actions — Build → SonarQube → Snyk  
> **Purpose:** This document explains how to use the Tabnine CLI in this repository to accelerate development, enforce code quality, and integrate AI assistance into the local and CI workflow.

---

## 📦 Installation

### Prerequisites
- Node.js ≥ 18 (LTS recommended)
- A valid Tabnine account (Enterprise or Pro)

### Install Tabnine CLI
```bash
npm install -g @tabnine/cli
```

### Verify installation
```bash
tabnine --version
```

---

## 🔐 Authentication

Tabnine CLI authenticates via your Tabnine account. **Never hardcode credentials.**

### Login (browser-based OAuth flow)
```bash
tabnine auth login
```

### Login with a token (for CI/headless environments)
```bash
# Store the token as a GitHub Actions secret: TABNINE_TOKEN
# Then reference it in the environment — never paste tokens directly
export TABNINE_TOKEN="${TABNINE_TOKEN}"
tabnine auth login --token "${TABNINE_TOKEN}"
```

### Check authentication status
```bash
tabnine auth status
```

### Logout
```bash
tabnine auth logout
```

---

## 🚀 Core CLI Commands

### Code Chat (ask questions about your codebase)
```bash
# Ask a question about a specific file
tabnine chat "Explain what this class does" --file src/main/java/org/springframework/samples/petclinic/rest/controller/PetRestController.java

# Ask about a directory
tabnine chat "What design patterns are used in the service layer?" --context src/main/java/org/springframework/samples/petclinic/service
```

### Code Review
```bash
# Review uncommitted changes
tabnine review

# Review a specific file
tabnine review --file src/main/java/org/springframework/samples/petclinic/rest/controller/PetRestController.java

# Review staged changes only
tabnine review --staged
```

### Code Generation
```bash
# Generate a unit test for a class
tabnine generate test --file src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java

# Generate documentation (Javadoc) for a file
tabnine generate docs --file src/main/java/org/springframework/samples/petclinic/rest/controller/OwnerRestController.java
```

### Fix Code Issues
```bash
# Fix issues in a specific file
tabnine fix --file src/main/java/org/springframework/samples/petclinic/rest/controller/PetRestController.java

# Fix all issues in a directory
tabnine fix --dir src/main/java
```

### Scan for Secrets
```bash
# Scan the entire repository for hardcoded secrets or credentials
tabnine scan secrets .

# Scan a specific file
tabnine scan secrets src/main/resources/application.properties
```

---

## 🔁 CI/CD Integration (GitHub Actions)

Tabnine CLI can be added as an additional job in `.github/workflows/build.yml`.  
Below is an example step to add **after the `build` job**:

```yaml
tabnine:
  name: Tabnine Code Review
  runs-on: ubuntu-latest
  needs: build
  env:
    TABNINE_TOKEN: ${{ secrets.TABNINE_TOKEN }}   # Store in GitHub Secrets — never hardcode

  steps:
    - name: Checkout repository
      uses: actions/checkout@v4

    - name: Set up Node.js (for Tabnine CLI)
      uses: actions/setup-node@v4
      with:
        node-version: '22'

    - name: Install Tabnine CLI
      run: npm install --global @tabnine/cli@latest   # Pin a version in production

    - name: Authenticate Tabnine CLI
      run: tabnine auth login --token "${TABNINE_TOKEN}"

    - name: Run Tabnine Code Review on changed files
      run: tabnine review --staged --output sarif --output-file tabnine-review.sarif
      continue-on-error: true

    - name: Upload Tabnine Review Report
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: tabnine-review
        path: tabnine-review.sarif
        retention-days: 14
```

> ⚠️ **Security note:** `TABNINE_TOKEN` must be added as a GitHub Actions secret under  
> **Repository → Settings → Secrets and variables → Actions → New repository secret**  
> It must **never** be committed to source code or printed in logs.

---

## 🛠️ Local Developer Workflow

### Typical daily workflow in this project

```bash
# 1. Pull latest changes
git pull origin main

# 2. Make your code changes (Java/Spring Boot)

# 3. Build & run tests locally (mirrors CI Job 1)
./mvnw -B verify --no-transfer-progress

# 4. Ask Tabnine to review your changes before committing
tabnine review

# 5. Ask Tabnine to explain a complex class or method
tabnine chat "Explain the transaction management in ClinicServiceImpl"

# 6. Generate missing tests for a service
tabnine generate test --file src/main/java/org/springframework/samples/petclinic/service/ClinicServiceImpl.java

# 7. Commit and push — CI pipeline handles SonarQube + Snyk automatically
git add .
git commit -m "feat: describe your change"
git push origin feature/your-branch
```

---

## 📂 Project-Specific Tips

### Working with the REST Controllers
```bash
# Get an overview of a controller's API surface
tabnine chat "List all REST endpoints in this file and their HTTP methods" \
  --file src/main/java/org/springframework/samples/petclinic/rest/controller/PetRestController.java
```

### Reviewing `pom.xml` dependencies
```bash
# Ask Tabnine to check for outdated or vulnerable dependencies
tabnine chat "Are there any outdated or potentially vulnerable dependencies in this pom.xml?" \
  --file pom.xml
```

### Understanding the CI Pipeline
```bash
# Ask Tabnine to explain the build workflow
tabnine chat "Explain what each job does in this workflow and the security controls in place" \
  --file .github/workflows/build.yml
```

### Generating Javadoc for undocumented classes
```bash
# Auto-generate Javadoc for all files under src/main/java
tabnine generate docs --dir src/main/java
```

---

## 🔒 Security Best Practices with Tabnine CLI

| Rule | Detail |
|------|--------|
| ✅ Use environment variables | Always pass `TABNINE_TOKEN` via env vars, never hardcode |
| ✅ Pin CLI versions in CI | Avoid `@latest` in CI — use a pinned version (e.g., `@1.x.x`) |
| ✅ Never log tokens | Ensure no `echo $TABNINE_TOKEN` or similar in scripts |
| ✅ Use GitHub Secrets | Store all tokens under repository Secrets — not env vars in the YAML |
| ❌ Never commit `.tabnine/` local cache with credentials | Add `.tabnine/` to `.gitignore` if it stores auth state |

---

## 🧩 IDE Integration (Companion to CLI)

The CLI works best alongside the Tabnine IDE plugin:

| IDE | Plugin |
|-----|--------|
| IntelliJ IDEA / JetBrains | [Tabnine for JetBrains](https://plugins.jetbrains.com/plugin/12798-tabnine-ai-autocomplete) |
| VS Code | [Tabnine for VS Code](https://marketplace.visualstudio.com/items?itemName=TabNine.tabnine-vscode) |
| Neovim | Available via `tabnine-nvim` plugin |

---

## ❓ Help & Reference

```bash
# Show all available commands
tabnine --help

# Help for a specific command
tabnine chat --help
tabnine review --help
tabnine generate --help
tabnine scan --help
tabnine auth --help
```

### Useful links
- 📖 [Tabnine CLI Documentation](https://docs.tabnine.com/main/editor-plugins/tabnine-cli)
- 🔐 [Tabnine Account & Token Management](https://app.tabnine.com/profile)
- 💬 [Tabnine Community & Support](https://community.tabnine.com)

---

## 📝 PR Review Checklist (when using Tabnine CLI in CI)

When adding the Tabnine CLI step to the CI pipeline, verify the following before merging:

- [ ] `TABNINE_TOKEN` secret is added to the repository — not hardcoded in YAML
- [ ] CLI version is pinned (not `@latest`) in `npm install --global @tabnine/cli@<version>`
- [ ] `tabnine auth login --token` does not echo the token to stdout
- [ ] SARIF report artifact retention is set to a reasonable period (14 days)
- [ ] `continue-on-error: true` is set so review failures don't block the deployment pipeline
- [ ] `.tabnine/` is added to `.gitignore` to prevent caching auth state in the repo
