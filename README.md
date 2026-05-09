# PixelMask

An LSPosed / Vector module that makes **Google Photos believe it is running on a Google Pixel**, unlocking Pixel-exclusive features (most notably *free unlimited Original-quality storage* on Pixel 1–5 device profiles) on any Android phone.

> Forked from [BaltiApps/Pixelify-Google-Photos](https://github.com/BaltiApps/Pixelify-Google-Photos) (EOL since 2024) and rebuilt on a modern stack: **Kotlin 2.2 · Jetpack Compose · Material 3 dynamic colors · YukiHookAPI 1.3 / KavaRef**. Package: `com.kinginu.pixelmask`.

---

## How it works

When Google Photos starts up it asks the system two questions to decide whether to enable Pixel-only features:

1. *Who made this phone, what model is it?* — reads the static fields on `android.os.Build` (`MANUFACTURER`, `MODEL`, `BRAND`, `FINGERPRINT`, …).
2. *Does this phone support feature X?* — calls `PackageManager.hasSystemFeature("com.google.android.feature.PIXEL_2016_EXPERIENCE")` and similar flags.

This module hooks **both** paths inside the Google Photos process only, and answers as if the device were a Pixel of the user's choosing.

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
   │    ① override Build.* static fields                         │
   │       └─ XposedHelpers.setStaticObjectField                 │
   │          (bypasses the public-static-final modifier)        │
   │    ② hook ApplicationPackageManager.hasSystemFeature        │
   │       └─ KavaRef:  resolve().firstMethod { … }.hook { … }   │
   │          · returns true  for features the chosen Pixel has  │
   │          · returns false for any Pixel feature it lacks     │
   │  }                                                          │
   └────────────────────────────────┬────────────────────────────┘
                                    │ injected at app load
                                    ▼
              ┌────────────────────────────────────┐
              │  Google Photos process             │
              │  sees a Pixel device →             │
              │  unlimited Original-quality backup │
              └────────────────────────────────────┘
```

The **two-list** trick (`featuresToEnable` vs. `featuresToBlock = allKnown - featuresToEnable`) is what lets you target an *older* Pixel from a phone whose Photos app already detects newer features: anything the chosen Pixel doesn't have is actively forced to `false`, not just left alone.

---

## Project layout

```
app/src/main/java/com/kinginu/pixelmask/
├── PixelMaskHookEntry.kt         ← Yuki entry, the only Xposed-side code
├── Constants.kt                  ← shared pref keys & package names
├── spoof/
│   └── DeviceProps.kt            ← table of Pixels, props, feature levels
├── ui/
│   ├── screens/                  ← Home / Settings (Compose)
│   ├── theme/                    ← Material 3 + dynamic color
│   └── components/
└── utils/
    └── ModuleStatus.kt           ← `hookedFlag` field flipped by the hook
```

---

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
| YukiHookAPI | 1.3.1 |
| KavaRef (core + extension) | 1.0.2 |
| Xposed API | 82 (`compileOnly`) |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |

Repos: `google()`, `mavenCentral()`, `https://api.xposed.info/`, `https://jitpack.io` (the last for the FreeReflection transitive dep pulled in by KavaRef).

---

## Install

1. Root the device with **Magisk**, **KernelSU**, or **APatch**.
2. Install an Xposed framework: [LSPosed](https://github.com/LSPosed/LSPosed) on Magisk, or [zygisk-vector](https://github.com/HuskyDG/zygisk-vector) + LSPosed on KernelSU.
3. Install the APK and enable the module.
4. Scope it to **Google Photos** **and** to **the module itself** — without the latter, the home screen stays on *Module Not Active* even when the hook is working.
5. Force-stop Google Photos, open the module, pick a target Pixel, reopen Photos.

### Tested

- KernelSU + zygisk-vector + LSPosed on Android 14+ (current dev target — Pixel XL profile, unlimited Original storage confirmed working)
- Magisk + LSPosed (BaltiApps's original target)

---

## Branching & releases

**Branches** — trunk-based, single long-lived branch.

| Branch | Purpose |
|---|---|
| `main` | Always green, always shippable. Direct commits OK for solo work; non-trivial changes go through short-lived `feature/*` or `fix/*` branches merged via PR. |
| `feature/<topic>`, `fix/<topic>` | Short-lived. Created locally or via PR. Deleted after merge. |

No `develop`, no `release/*`, no `hotfix/*` — flat is good for a one-person module.

**Tags** — releases get a tag in the form **`<versionCode>-<versionName>`** (e.g. `7-1.0.6`). The `versionCode` is a monotonic int the OS uses for the install-upgrade decision; `versionName` is the human-readable semver. The Release Module workflow tags this automatically — no need to tag by hand.

**Continuous builds (debug)** — every push to `main` and every PR runs `./gradlew :app:assembleDebug` and uploads the APK as a workflow artifact (`PixelMask-debug-<sha>`). 14-day retention. Find them under *Actions → Build Debug → \<run\> → Artifacts*.

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

**Signing** — releases are signed by a single long-lived keystore stored as repo secrets. Devices that already have an older PixelMask install can upgrade in place; signature stays the same forever. The workflow consumes four secrets: `RELEASE_KEYSTORE_B64` (base64 of the keystore file), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Generate the keystore once locally with `keytool -genkey -keyalg RSA -keysize 2048 -validity 36500`, base64-encode it, paste into Settings → Secrets → Actions, and back the original up offline (lose the file or its passwords and you can never push another update).

---

## Credits

- Based on [Pixelify-Google-Photos](https://github.com/BaltiApps/Pixelify-Google-Photos) by BaltiApps (MIT, EOL 2024). Thank you for the original work.
- Launcher icon adapted from [Now in Android](https://github.com/android/nowinandroid) by The Android Open Source Project (Apache-2.0). The Bugdroid silhouette path is unchanged; the original `{}` mark above it was removed and replaced with a 4-blade pinwheel.

## License

MIT — see [LICENSE](LICENSE).

## Disclaimer

For research and educational use. No warranty. The user is responsible for any use of the module, including data loss or legal consequences.
