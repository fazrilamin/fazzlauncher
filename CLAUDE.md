# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

The full playbook, safety rules and code map are in AGENTS.md (shared with Codex and other agents):

@AGENTS.md

## Claude Code specifics

- On Windows, Claude Code has both PowerShell and Bash tools. Use **Bash** for anything binary: `adb exec-out screencap -p > shot.png`, `adb pull`. PowerShell redirection corrupts PNGs.
- In Bash, prefix `adb pull`/`push` of device paths with `MSYS_NO_PATHCONV=1`.
- To see the head unit's screen, take a screenshot and open the PNG with the Read tool.
- For long log captures (waiting for a rare crash), run the logcat loop as a background command, and stop it when the owner is done.
- `adb` is often not on PATH. `tools/setup-toolchain.ps1` installs platform-tools to `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`.
