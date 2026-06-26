# apronTERM

A Swing-based terminal for Windows that reuses your **Windows Terminal profiles** and organises
terminal tabs into switchable **projects** — so you only open the tabs you actually need right now,
and get them back next time exactly as you left them.

Built on [JediTerm](https://github.com/JetBrains/jediterm) (terminal emulator) and
[pty4j](https://github.com/JetBrains/pty4j) (ConPTY pseudo-console), with a dark
[FlatLaf](https://www.formdev.com/flatlaf/) look and feel.

## Features

- **Reuses Windows Terminal profiles** — reads `settings.json` directly (cmd, PowerShell, mingw, …),
  including the `profiles.defaults` and the default profile.
- **Live reload** — a built-in editor for `settings.json`; on save (or when an external editor
  changes the file) profiles reload immediately, so new tabs pick up the changes at once.
- **Projects** — named sets of tabs (profile + starting directory + title). Switch between them
  (replace current tabs) or add them to the current window.
- **Session restore** — the tabs that were open when you quit are reopened on the next start,
  along with the window size/position.
- **Dark theme** by default (switchable to light), with larger, higher-contrast terminal text.
- **Per-user config** under `%LOCALAPPDATA%\apronTERM` — nothing is written into the program folder.

## Requirements

- Windows 10/11
- JDK 21+
- Maven 3.9+
- Windows Terminal installed (for its `settings.json` profiles) — optional, but that's the point.

## Install
Download and save https://github.com/jposterloh/apronTERM/blob/main/apronTERM_Starter.zip to your computer. Extract the zip content to a new folder on your computer, e.g. in Program Files or your local App folder. Create shortcuts to the exe for Desktop, Startmenü or Task area. 

## Run the app

Start apronTERM.exe. The executable will check for updates, download the latest version and then start the VM and the App. 


# Build

```cmd/powershell
mvn -DskipTests clean package
```

This produces a single runnable fat-jar: `target\apronterm-0.1.0.jar`.

## Run Developer

```cmd/powershell
javaw -jar target\apronterm-0.1.0.jar
```

Use `java` instead of `javaw` to launch with a console window. During development you can also run
straight from sources:

```cmd/powershell
mvn exec:java
```

## Launcher & auto-update

For end users, apronTERM is started through the native **starter** (a jlaunch derivative —
`JavaStarter.exe` + `JVMStarter.dll`) rather than a script. The starter shows a splash screen,
checks for updates over HTTPS, downloads/extracts the new version if needed, and then starts the
JVM via JNI. It carries **no JRE of its own** — a JRE is bundled in the release ZIP, so the runtime
is updated alongside the app.

The starter expects two release assets, which this repo publishes to **GitHub Releases**:

- `update.xml` — the remote version file (`/information/pkgrel`), served loose. The starter polls it
  every launch.
- `apronterm.zip` — the payload it unzips into `%LOCALAPPDATA%\apronTERM`, containing the fat-jar,
  a `version.xml` local-version marker (`/properties/entry[@key='version']`), and a `java/` JRE.

When `update.xml`'s `pkgrel` differs from the installed `version.xml` (or the app isn't installed
yet), the starter downloads and extracts `apronterm.zip`. The starter is configured with the stable
URLs `…/releases/latest/download/update.xml` and `…/releases/latest/download/apronterm.zip`, so it
always tracks the newest release without per-version reconfiguration.

### Cutting a release

Both XML files and the ZIP are produced by `mvn verify` (see `pom.xml`'s antrun execution) from the
`update.xml.tmpl` / `version.xml.tmpl` templates, with a timestamp build number filled in. To build
them locally:

```powershell
mvn -DskipTests clean verify   # -> target\apronterm.zip, target\update.xml, target\version.xml
```

In CI, pushing a `release-*` tag triggers `.github/workflows/release.yml`, which runs the build on a
**Windows** runner (the bundled JRE is `jlink`ed and is OS-specific) and uploads `apronterm.zip` +
`update.xml` as assets of a GitHub release named after the tag:

```powershell
git tag release-2026-06
git push origin release-2026-06
```

## Concepts

### Profiles

Profiles come from Windows Terminal's `settings.json`. apronTERM auto-detects the file at the usual
Store-install location and falls back to other known paths; you can override it via
`config.json` (see below).

A profile's `commandline` is environment-expanded (`%SystemRoot%` → `C:\WINDOWS`), split into
arguments respecting quotes, and `.cmd`/`.bat` shells are wrapped in `cmd.exe /c` (ConPTY cannot
launch batch files directly).

### Projects

A **project** is a named list of tabs, each tab being a *profile + starting directory + optional
title*. Define them via **Projekte → Projekte verwalten…**, or capture the currently open tabs with
**Projekte → Aktuelle Tabs als Projekt speichern…**. Open a project from the toolbar:

- **Wechseln** — close the current tabs and open the project's tabs.
- **Hinzufügen** — append the project's tabs to the current ones.

### Session

When you close the window, the open tabs (and window geometry) are saved and restored on the next
launch. This is independent of projects: it remembers your actual last state, including ad-hoc tabs.

## Configuration

Everything lives under `%LOCALAPPDATA%\apronTERM`:

- `config.json` — program settings:
  - `theme` — `"dark"` (default) or `"light"`.
  - `wtSettingsPath` — explicit path to Windows Terminal's `settings.json` (omit/null to auto-detect).
- `projects.json` — your defined projects.
- `session.json` — last open tabs + window bounds (managed automatically).

## Diagnostics

A headless tool prints the parsed profiles and the exact argv apronTERM would launch — handy for
debugging a profile that won't start:

```powershell
java -cp target\apronterm-0.1.0.jar dev.apronterm.tools.ProfileDump
```

## Architecture

Java package `dev.apronterm`:

- `app` — paths (`%LOCALAPPDATA%\apronTERM`), `ApronTermConfig`, shared Jackson mappers.
- `wt` — Windows Terminal profile model, JSONC parsing, and the file-watching reload service.
- `project` — `TabSpec` / `Project` model and `ProjectStore` (projects + session persistence).
- `terminal` — command-line resolution, the pty4j↔JediTerm connector, dark color scheme, and the
  `TerminalFactory` that launches shells on a pseudo-console.
- `ui` — `MainFrame`, the closeable tab header, the `settings.json` editor, and the project manager.
- `ApronTerm` — entry point.

## Known limitations

- **Source-generated profiles** (Azure Cloud Shell, Visual Studio dev prompts) have no `commandline`
  in `settings.json` and can't be launched yet. WSL profiles are launched best-effort via
  `wsl.exe -d <name>`.
- **Live reload** updates the profile list and affects *new* tabs; already-running shells keep their
  current settings (a running process can't be restyled retroactively).
- Windows only (relies on ConPTY and the Windows Terminal settings layout).

## Tech stack & licenses

- [JediTerm](https://github.com/JetBrains/jediterm) — terminal emulator (Apache-2.0)
- [pty4j](https://github.com/JetBrains/pty4j) — pseudo-console / ConPTY (EPL-1.0)
- [FlatLaf](https://www.formdev.com/flatlaf/) — look and feel (Apache-2.0)
- [Jackson](https://github.com/FasterXML/jackson) — JSON (Apache-2.0)

apronTERM itself is licensed under the **Apache License 2.0** (see `LICENSE`).
