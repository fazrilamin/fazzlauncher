# 8. Publishing a new version on GitHub

How to put a new version of Fazz Launcher on GitHub: the source, the docs, and a downloadable APK on the **Releases** page. It uses **GitHub Desktop** (no command line needed). The same steps with plain `git` are at the end.

## Before you start: get the release ready

1. **Bump the version** in `FazzLauncher/AndroidManifest.xml`:
   - `android:versionCode`: add 1 (for example 1 → 2). Android uses it to decide that the new APK is an update.
   - `android:versionName`: what people see, e.g. `2.0`. The first number is for big new features, the second for fixes (2.0 → 2.1).
2. **Write what changed** at the top of [`CHANGELOG.md`](../CHANGELOG.md).
3. **Build** with `bash FazzLauncher/build.sh`, and test the APK on the unit.
4. **Copy the APK** to `release/FazzLauncher-<version>.apk`, and remove the older one from `release/`. Older versions stay downloadable on the Releases page.
5. **Update the SHA-256** in `README.md`. In Git Bash: `sha256sum release/FazzLauncher-2.0.apk`.
6. **Privacy check** (screenshots and docs are public):
   - no car make or model, no home town, no Wi-Fi name or LAN IP addresses, no `C:\Users\<you>` paths;
   - screenshots taken with `--ez hide_town true`, and the map blurred in skin 2 screenshots;
   - **never** the `keystore/` folder or any `.jks` file. `.gitignore` already blocks them; double-check anyway.

## One time only: clone the repo with GitHub Desktop

If you uploaded the first version through the github.com website, you don't have a local clone yet. Make one:

1. Open GitHub Desktop and sign in (**File → Options → Accounts**).
2. **File → Clone repository → GitHub.com**, pick `fazzlauncher`, and choose a local folder, for example `Documents\GitHub\fazzlauncher`. Click **Clone**.

From now on, this clone is where you publish from. Copy your updated files into it, as below.

## Each release

### 1. Copy the updated files into the clone

Copy the contents of your working folder over the clone, replacing existing files. Copy **everything except** `FazzLauncher/build/` and `FazzLauncher/keystore/`.

If you deleted a file (like the old APK in `release/`), delete it in the clone too.

### 2. Review the changes

In GitHub Desktop, the **Changes** tab on the left lists every changed, added and deleted file. Click each one to see the difference.
- Check that no `.jks`, `.idsig`, `build/` file or personal detail is in the list.
- Untick anything that shouldn't be published.

### 3. Commit and push

1. At the bottom left, **Summary**: a short title, e.g. `Fazz Launcher 2.0: skins (Classic, Map, Twin dials)`.
   **Description** (optional): a few lines from the changelog.
2. Click **Commit to main**.
3. Click **Push origin** at the top. Your files are now on GitHub.

### 4. Create the release with the APK

1. On github.com, open the repo and click **Releases** (right side) → **Draft a new release**.
2. **Choose a tag** → type `v2.0` → **Create new tag: v2.0 on publish**. Target: `main`.
3. **Release title:** `Fazz Launcher 2.0`.
4. **Description:** paste the 2.0 section of `CHANGELOG.md`, and add the APK's SHA-256.
5. Drag `release/FazzLauncher-2.0.apk` into **Attach binaries**. Wait for the upload to finish.
6. Click **Publish release**.

The README's "Releases" link now shows it, and people can download the APK directly.

### Optional: a release for 1.0 too

So the history is complete: **Draft a new release** → tag `v1.0`, target = the first commit (Choose a tag → type `v1.0` → **Target** → Recent commits). Attach the old 1.0 APK if you still have it.

## Next time

Repeat "Before you start" and "Each release" with the new number: `versionCode 3`, `versionName 2.1`, tag `v2.1`.

## The same with plain git (reference)

```bash
git clone https://github.com/fazrilamin/fazzlauncher.git      # one time
# copy the updated files in, then:
git status                                                    # review: no keystore, no build/
git add -A
git commit -m "Fazz Launcher 2.0: skins (Classic, Map, Twin dials)"
git push
git tag v2.0
git push origin v2.0
# then on github.com: Releases → Draft a new release → tag v2.0 → attach the APK → Publish
# (or, with the GitHub CLI: gh release create v2.0 release/FazzLauncher-2.0.apk --title "Fazz Launcher 2.0" --notes-file notes.md)
```
