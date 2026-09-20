# Building Debug APKs Skill Design

## Goal

Create a reusable personal Codex skill named `building-debug-apks` for requests to build a Debug APK from an existing Android project. The skill must work across projects instead of assuming the layout or configuration of `netcheck-tool`.

## Scope

The skill will:

- recognize Android Gradle projects and identify the project root;
- inspect Gradle settings and available application modules without changing source files;
- prefer the project's Gradle Wrapper and fall back to an installed `gradle` command only when a wrapper is unavailable;
- choose an unambiguous Debug build task, accounting for modules and product flavors;
- run the build after reporting the intended command;
- locate generated APK files and report their paths, sizes, and build outcome;
- diagnose common JDK, Android SDK, Gradle Wrapper, dependency download, and compilation failures.

The skill will not build Release APKs or Android App Bundles, configure signing, install packages on devices, publish artifacts, or silently modify Gradle configuration.

## Structure

Install the skill in the personal Codex skill directory as:

```text
building-debug-apks/
|-- SKILL.md
`-- agents/
    `-- openai.yaml
```

Keep the workflow in `SKILL.md`. A helper script is unnecessary because Gradle project layouts and variants require inspection and judgment, while the actual build remains a standard Gradle invocation.

## Workflow

1. Resolve the requested project directory and confirm it contains Android Gradle configuration.
2. Inspect `settings.gradle` or `settings.gradle.kts`, module build files, and wrapper files.
3. Prefer `./gradlew`; if it is missing, use an installed `gradle` only after verifying availability.
4. Identify application modules and Debug variants. Use `assembleDebug` for an ordinary single-application project. If multiple application modules or Debug variants make the target ambiguous, ask the user to choose rather than guessing.
5. Execute the selected build without rewriting build files, creating signing material, or bypassing repository instructions.
6. Search module build output directories for generated Debug APKs and report absolute paths and file sizes.
7. On failure, preserve the useful Gradle error and explain the most likely actionable category. Do not claim success unless the build command exits successfully and at least one APK exists.

## Safety and Project Boundaries

- Read and obey repository-local instructions such as `AGENTS.md` before building.
- Treat dependency downloads and writes outside the permitted workspace as operations that may require user approval.
- Do not run cleanup tasks, delete build outputs, edit configuration, or accept licenses unless the user separately authorizes them.
- Do not expose credentials, signing files, private repository URLs, or environment-variable values in output.

## Validation

Validate the skill metadata and scaffold with the skill validator. Exercise it against representative cases in isolated temporary directories:

- a standard single-module Android application with a Gradle Wrapper;
- a project without wrapper scripts, verifying a safe system-Gradle fallback or a clear prerequisite error;
- a multi-module or flavored application where the target is ambiguous;
- a failed build, verifying that no APK success claim is made.

The skill is complete when it selects safe Debug build commands, preserves project state, reports real APK artifacts, and handles ambiguous or failed builds explicitly.
