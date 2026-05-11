# PixelMask — Internals

Implementation notes, build instructions, and release process. The user-facing
documentation lives in the project [README](../README.md).

## How it works

When Google Photos starts up it asks the system two questions to decide whether
to enable Pixel-only features:

1. *Who made this phone, what model is it?* — reads the static fields on
   `android.os.Build` (`MANUFACTURER`, `MODEL`, `BRAND`, `FINGERPRINT`, …).
2. *Does this phone support feature X?* — calls
   `PackageManager.hasSystemFeature("com.google.android.feature.PIXEL_2016_EXPERIENCE")`
   and similar flags.

PixelMask hooks **both** paths inside the Google Photos process only, and
answers as if the device were a Pixel of the user's choosing.

```
   ┌─────────────────────────────────────────────────────────────┐
   │  PixelMask UI           (Compose · Material 3 · dynamic)    │
   │  ─────────────────────                                      │
   │  · pick a target Pixel  · master enable  · verbose logs     │
   └────────────────────────────────┬────────────────────────────┘
                                    │ writes
                                    ▼
                        SharedPreferences
                       (xposedsharedprefs=true,
                        readable from the hook side)
                                    │ reads
                                    ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  PixelMaskHookEntry     @InjectYukiHookWithXposed           │
   │  ─────────────────                                          │
   │  KSP generates the classic Xposed entry point and the       │
   │  assets/xposed_init pointer file at build time.             │
   │                                                             │
   │  loadApp("com.google.android.apps.photos") {                │
   │    1. override Build.* static fields                        │
   │       └─ XposedHelpers.setStaticObjectField                 │
   │          (bypasses the public-static-final modifier)        │
   │    2. hook ApplicationPackageManager.hasSystemFeature       │
   │       └─ KavaRef:  resolve().firstMethod { … }.hook { … }   │
   │          · returns true  for features the chosen Pixel has  │
   │          · returns false for any Pixel feature it lacks     │
   │  }                                                          │
   └────────────────────────────────┬────────────────────────────┘
                                    │ injected at app load
                                    ▼
   ┌─────────────────────────────────────────────────────────────┐
   │  Google Photos process                                      │
   │  sees a Pixel device →                                      │
   │  unlimited Original-quality backup                          │
   └─────────────────────────────────────────────────────────────┘
```

The **two-list trick** (`featuresToEnable` vs.
`featuresToBlock = allKnown - featuresToEnable`) is what lets you target an
*older* Pixel from a phone whose Photos app already detects newer features:
anything the chosen Pixel doesn't have is actively forced to `false`, not just
left alone.

`FeatureLevel` is an ordered enum (`PIXEL_2016` … `PIXEL_2025`); each
`DeviceEntry` references it by enum reference, and "features up to and
including" is computed via `enum.ordinal`. That's what keeps the device table
typo-proof — adding a new device with a misspelled level is a compile error,
not a silent "spoof nothing" at runtime.

## Project layout

```
app/src/main/java/com/kinginu/pixelmask/
├── PixelMaskHookEntry.kt         ← Yuki entry, the only Xposed-side code
├── Constants.kt                  ← shared pref keys & package names
├── spoof/
│   └── DeviceProps.kt            ← table of Pixels, props, FeatureLevel enum
├── ui/
│   ├── screens/                  ← Home / Settings (Compose)
│   └── components/               ← StatusCard, MasterSwitchCard, ...
└── utils/
    ├── ModuleStatus.kt           ← `hookedFlag` field flipped by the hook
    └── Utils.kt                  ← intent helpers, issue-template launcher
```

## Build

```bash
./gradlew :app:assembleDebug
```

Stack:

| Layer | Version |
|---|---|
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.2.10 |
| Compose Compiler Gradle Plugin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2026.05.00 |
| AndroidX core-ktx / activity-compose / navigation-compose | 1.18.0 / 1.13.0 / 2.9.8 |
| YukiHookAPI | 1.3.1 |
| KavaRef (core + extension) | 1.0.2 |
| Xposed API | 82 (`compileOnly`) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

Repos: `google()`, `mavenCentral()`, `https://api.xposed.info/`,
`https://jitpack.io` (the last for the FreeReflection transitive dep pulled
in by KavaRef).

## README assets pipeline

The two banners at the top of the README are produced offline from the
screenshots under `docs/screenshots/` and committed.

```
docs/
├── build-banner.sh      # ffmpeg hstack of three panels each, scaled to common height
├── banner-app.png       # title | Home | Settings
├── banner-proof.png     # account menu | Photos settings | Backup (with red highlights)
└── screenshots/
    ├── app-title.png    # generated by Chrome headless from the launcher SVG
    ├── app-home.jpg
    ├── app-settings.jpg
    ├── gphotos-account.jpg
    ├── gphotos-settings.jpg
    └── gphotos-backup.jpg
```

`build-banner.sh` accepts `.png`, `.jpg`, or `.jpeg` for each panel and
auto-detects. The output is always PNG. To rebuild after replacing a
screenshot, just re-run the script.

The `app-title.png` panel was rendered by feeding `/tmp/title.html` (a wrapper
around the launcher's vector drawable as inline SVG, plus a `<text>`) into
`Google Chrome --headless --screenshot`. The launcher icon's `<vector>` path
data is reused verbatim so the banner icon and the on-device launcher icon
stay in sync.

The Google Photos screenshots were redacted by hand — each one was opened in
an image editor, the Google account email/name/avatar were painted out, and
the red highlight rectangle was drawn around the next-tap target.

## Branching & releases

**Branches** — trunk-based, single long-lived branch.

| Branch | Purpose |
|---|---|
| `main` | Always green, always shippable. Direct commits OK for solo work; non-trivial changes go through short-lived `feature/*` or `fix/*` branches merged via PR. |
| `feature/<topic>`, `fix/<topic>` | Short-lived. Created locally or via PR. Deleted after merge. |

No `develop`, no `release/*`, no `hotfix/*` — flat is good for a one-person
module.

**Tags** — releases get a tag in the form **`<versionCode>-<versionName>`**
(e.g. `7-1.0.6`). The `versionCode` is a monotonic int the OS uses for the
install-upgrade decision; `versionName` is the human-readable semver. The
Release Module workflow tags this automatically — no need to tag by hand.

> The LSPosed module repo (`modules.lsposed.org`) requires this exact tag
> shape. If you ever rename the workflow, keep `<code>-<name>` or its bot
> will refuse the release.

**Continuous builds (debug)** — every push to `main` and every PR runs
`./gradlew :app:assembleDebug` and uploads the APK as a workflow artifact
(`PixelMask-debug-<sha>`). 14-day retention. Find them under
*Actions → Build Debug → \<run\> → Artifacts*.

**Release process**

1. Land everything you want shipped on `main`.
2. *Actions* → **Release Module** → **Run workflow**, fill in:
   - `version_name` → e.g. `1.0.6` (semver, no leading `v`)
   - `changelog` → optional, copy from the *Release Drafter* draft
3. The workflow:
   1. Reads the previous `latest_version_code` from `update_info.json`, increments by 1
   2. Updates `update_info.json` and pushes that commit back to `main`
   3. Decodes the release keystore from `RELEASE_KEYSTORE_B64` (a GitHub Actions secret, see *Signing* below) and builds `assembleRelease` signed with it
   4. Renames the APK to `PixelMask-<versionName>.apk`
   5. Creates a GitHub Release tagged `<code>-<name>` with the APK attached
4. The in-app updater fetches `update_info.json` from `main` and points users at the new release.

**Signing** — releases are signed by a single long-lived keystore stored as
repo secrets. Devices that already have an older PixelMask install can upgrade
in place; signature stays the same forever. The workflow consumes four
secrets: `RELEASE_KEYSTORE_B64` (base64 of the keystore file),
`KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Generate the keystore once
locally with `keytool -genkey -keyalg RSA -keysize 2048 -validity 36500`,
base64-encode it, paste into Settings → Secrets → Actions, and back the
original up offline (lose the file or its passwords and you can never push
another update).

**LSPosed module repo mirror** — PixelMask is also published to
`Xposed-Modules-Repo/com.kinginu.pixelmask`, which is what LSPosed Manager's
in-app module catalog scrapes. That repo only carries `SUMMARY.md`,
`README.md` (which links back here), and the release artifacts; no source
code lives there.

The release workflow's `Mirror release to LSPosed module repo` step pushes
the just-built APK to the mirror using a **Classic PAT** stored as the
`LSPOSED_MIRROR_TOKEN` secret. Fine-grained PATs and a scheduled workflow
on the mirror are both blocked by org-level policy (see "What we tried
first" below); Classic PATs slip through because they're tied to the
issuing user account, not the resource org.

### Token setup (one-time)

1. GitHub → **Settings** → **Developer settings** → **Personal access
   tokens** → **Tokens (classic)** → **Generate new token (classic)**.
2. *Note:* `PixelMask LSPosed mirror`. *Expiration:* 1 year (rotate before
   expiry — the next release will warn-and-skip otherwise).
3. *Scopes:* `public_repo` only. Don't tick `repo` — `public_repo` is
   enough to create releases on a public repo we admin, and the narrower
   scope limits blast radius if the token leaks.
4. Generate, copy the `ghp_...` value once (GitHub won't show it again).
5. In `kinginu/PixelMask` → **Settings → Secrets and variables → Actions**
   → **New repository secret**:
   - *Name:* `LSPOSED_MIRROR_TOKEN`
   - *Secret:* the `ghp_...` value

The next `Release Module` run will mirror automatically. If the token gets
revoked or expires, the source release still ships — the mirror step just
warns and exits 0 so the maintainer can rotate the token (or fall back to
the local script) without a failed CI run blocking the rest of the
pipeline.

### Fallback: local helper script

If the PAT is in mid-rotation or temporarily revoked,
`scripts/mirror-release.sh` does the same job from a local clone using
your personal `gh auth` session:

```bash
scripts/mirror-release.sh              # mirrors the source repo's "latest"
scripts/mirror-release.sh 13-1.0.17    # mirrors a specific tag
```

Idempotent — short-circuits if the tag is already mirrored. Useful for
the very first release before the PAT is set up, and as a safety net
during PAT rotation.

### What we tried first

| Approach | Status |
|---|---|
| Push from `release.yml` using a **fine-grained PAT** | Resource-owner dropdown on the token-creation page doesn't list `Xposed-Modules-Repo` at all — the org has fine-grained PATs disabled, so no token of this type can target the mirror. |
| Pull on the mirror via a **scheduled GitHub Actions workflow** | API returns `GitHub Actions is disabled on this repository by the organization`. The org has Actions off org-wide with no per-repo override. |
| Push from `release.yml` using a **GitHub App** installation token | Would have worked in principle (App auth is a separate surface) but adds enough setup overhead — register App, generate key, install on two repos, store ID + private key — that Classic PAT is the better cost-for-benefit. Revived from git history if needed. |
| Push from `release.yml` using a **Classic PAT** with `public_repo` | **Current approach.** Tied to issuing user, not gated by the resource org's fine-grained policies. Verified working. |

If a future policy shift re-opens the easier paths, revive the
appropriate workflow from git history:
- commit `722ca9e` — scheduled-on-mirror variant
- commit `c9cd7a2` — GitHub App variant

## Tested environments

- KernelSU + zygisk-vector + LSPosed on Android 14+ (current dev target — Pixel profile, unlimited Original-quality storage confirmed working)
- Magisk + LSPosed (BaltiApps's original target)
