# Building Debug APKs Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Install and validate a reusable personal Codex skill that builds Debug APKs from existing Android Gradle projects.

**Architecture:** Use a concise, instruction-only skill in `/home/ubuntu/.codex/skills/building-debug-apks`. The skill inspects each target project, selects an unambiguous application module and Debug variant, invokes the project Gradle Wrapper when available, and verifies a real APK artifact before reporting success. Behavioral evaluations cover ordinary, ambiguous, and failed builds without adding project-specific automation.

**Tech Stack:** Codex Skills (`SKILL.md`, `agents/openai.yaml`), Gradle/Android Gradle Plugin commands, bundled skill initializer and validator.

---

### Task 1: Establish behavior baselines

**Files:**
- Create temporarily: `/tmp/building-debug-apks-evaluations/`
- Reference: `docs/superpowers/specs/2026-09-20-building-debug-apks-skill-design.md`

- [ ] **Step 1: Prepare three evaluation requests**

Use isolated temporary fixture descriptions rather than the current working project:

```text
Evaluation A: A single-module Android app has settings.gradle.kts, app/build.gradle.kts,
and executable gradlew. The user asks for a Debug APK. State the inspection, exact build
task, success criteria, and artifact discovery behavior.

Evaluation B: An Android project has :consumer and :admin application modules, each with
Debug variants. The user asks for "the Debug APK" and a deadline is near. Decide whether
to guess a module or request the missing choice.

Evaluation C: Gradle exits nonzero after a Kotlin compilation error, but an old APK remains
under app/build/outputs/apk/debug. Decide whether the build succeeded and what to report.
```

- [ ] **Step 2: Run the evaluations without the new skill**

Dispatch fresh agents without access to `building-debug-apks`. Require each agent to choose and explain an action. Save their exact responses under `/tmp/building-debug-apks-evaluations/baseline-a.txt`, `baseline-b.txt`, and `baseline-c.txt`.

Expected: At least one response omits a required invariant or makes an unsafe assumption, such as using an unqualified root task, guessing a module, editing project configuration, or treating a stale APK as new success. Record the observed gaps verbatim; do not create the skill before this baseline is complete.

- [ ] **Step 3: Summarize only observed gaps**

Create `/tmp/building-debug-apks-evaluations/baseline-summary.md` containing a short list of observed failures that the minimal skill must address.

### Task 2: Install the minimal personal skill

**Files:**
- Create: `/home/ubuntu/.codex/skills/building-debug-apks/SKILL.md`
- Create: `/home/ubuntu/.codex/skills/building-debug-apks/agents/openai.yaml`

- [ ] **Step 1: Initialize the skill directory**

Run with permission to write to the personal Codex directory:

```bash
python3 /home/ubuntu/.codex/skills/.system/skill-creator/scripts/init_skill.py \
  building-debug-apks \
  --path /home/ubuntu/.codex/skills \
  --interface 'display_name=Build Debug APKs' \
  --interface 'short_description=Build Android Debug APKs safely' \
  --interface 'default_prompt=Use $building-debug-apks to build a Debug APK for the current Android project.'
```

Expected: the initializer creates `SKILL.md` and `agents/openai.yaml` without example resource directories.

- [ ] **Step 2: Replace the scaffold with the minimal skill**

Set `/home/ubuntu/.codex/skills/building-debug-apks/SKILL.md` to:

```markdown
---
name: building-debug-apks
description: Use when an existing Android Gradle project needs a Debug APK built, packaged, assembled, or located for testing.
---

# Building Debug APKs

Build the project's own Debug variant without changing its source or Gradle configuration. A successful command alone is insufficient: verify the resulting APK.

## Workflow

1. Resolve the target project root and read its repository instructions. Confirm Android Gradle files exist.
2. Inspect settings, module build files, Gradle Wrapper files, and relevant local configuration without printing secrets. Identify modules applying `com.android.application` and their Debug variants.
3. If more than one application module or matching Debug variant is plausible, ask the user to choose. Do not guess because of deadlines or existing outputs.
4. Prefer the project wrapper (`./gradlew` on Unix, `gradlew.bat` on Windows). If it is absent, verify an installed `gradle` command before using it; otherwise report the missing prerequisite.
5. Run the most specific task from the project root, normally `:<module>:assembleDebug` or `:<module>:assemble<Flavor>Debug`. Do not run `clean`, edit build files, create signing material, accept licenses, or install the APK unless separately requested and authorized.
6. Treat the build as successful only when Gradle exits zero and a matching APK exists beneath `<module>/build/outputs/apk/`. Report the command, absolute APK path, and size. Never treat a stale APK as proof that a failed build succeeded.

## Failure handling

Preserve the useful Gradle error and classify it when evidence supports one of these actions:

| Evidence | Action |
|---|---|
| Wrapper unavailable and no usable system Gradle exists | Report the exact prerequisite; do not rewrite the project automatically. |
| Java/Gradle compatibility error | Report detected Java and Gradle versions plus the relevant requirement. |
| Android SDK component or license missing | Name the missing component or license; request authority before installing or accepting anything. |
| Dependency resolution/network failure | Report the failed repository or dependency without exposing credentials. |
| Compilation/test/resource failure | Point to the first actionable source error; do not claim an APK was produced. |

## Boundaries

This skill builds Debug APKs only. It does not build Release APKs or AABs, configure signing, publish artifacts, or install packages on devices.

## Example

For a single `:app` application module, run `./gradlew :app:assembleDebug`, then verify and report APKs under `app/build/outputs/apk/`.
```

- [ ] **Step 3: Verify interface metadata**

Ensure `/home/ubuntu/.codex/skills/building-debug-apks/agents/openai.yaml` contains:

```yaml
interface:
  display_name: "Build Debug APKs"
  short_description: "Build Android Debug APKs safely"
  default_prompt: "Use $building-debug-apks to build a Debug APK for the current Android project."
```

Keep implicit invocation enabled by default; do not add external tool dependencies.

### Task 3: Validate structure and metadata

**Files:**
- Test: `/home/ubuntu/.codex/skills/building-debug-apks/SKILL.md`
- Test: `/home/ubuntu/.codex/skills/building-debug-apks/agents/openai.yaml`

- [ ] **Step 1: Run the bundled validator**

```bash
python3 /home/ubuntu/.codex/skills/.system/skill-creator/scripts/quick_validate.py \
  /home/ubuntu/.codex/skills/building-debug-apks
```

Expected: `Skill is valid!`

- [ ] **Step 2: Check concision and unfinished scaffolding**

```bash
wc -w /home/ubuntu/.codex/skills/building-debug-apks/SKILL.md
rg -n 'TODO|TBD|Example [123]|Replace this|placeholder' \
  /home/ubuntu/.codex/skills/building-debug-apks
```

Expected: fewer than 500 words and no unfinished-scaffold matches.

- [ ] **Step 3: Inspect the final installed files**

```bash
sed -n '1,260p' /home/ubuntu/.codex/skills/building-debug-apks/SKILL.md
sed -n '1,120p' /home/ubuntu/.codex/skills/building-debug-apks/agents/openai.yaml
```

Expected: the metadata matches the skill name and the workflow contains all safety and success invariants from the design.

### Task 4: Forward-test skill behavior

**Files:**
- Test: `/home/ubuntu/.codex/skills/building-debug-apks/SKILL.md`
- Create temporarily: `/tmp/building-debug-apks-evaluations/with-skill-*.txt`

- [ ] **Step 1: Re-run the three evaluations with the installed skill**

Dispatch fresh agents with this request prefix:

```text
Use $building-debug-apks at /home/ubuntu/.codex/skills/building-debug-apks to complete this realistic request. Read its SKILL.md completely before deciding.
```

Append Evaluation A, B, or C from Task 1 and save each exact response.

Expected:

- A selects a module-qualified Debug task and requires both exit code zero and a real matching APK.
- B asks the user to choose between ambiguous application modules instead of guessing.
- C reports failure despite the stale APK and preserves the actionable compiler error.

- [ ] **Step 2: Compare against baseline and refine only if demonstrated**

If an evaluation fails, update the smallest relevant instruction in `SKILL.md`, rerun the validator, and repeat only the failed evaluation. Do not add speculative rules.

- [ ] **Step 3: Final verification**

Run the validator again and inspect the installed paths. Confirm no target Android project was modified and no Release, signing, install, or publication action was performed.

Expected: all three behavioral evaluations satisfy their criteria and `quick_validate.py` prints `Skill is valid!`.
