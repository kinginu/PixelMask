# Rootless feasibility, perk enforcement & storage-clawback risk

Research notes answering three questions that come up repeatedly about PixelMask:

1. Can PixelMask work **without root** (Shizuku, LSPatch, app-cloning, …)?
2. What does Google actually **check / detect** to grant or revoke the Pixel
   unlimited-backup perk — and does running inside the genuine, Google-signed
   Photos app (as PixelMask does) hide the spoof better than a re-signed
   ReVanced build?
3. When the perk is later **revoked**, do photos already backed up as
   "free / does not count against quota" get **retroactively re-counted**
   against the 15 GB Google-account quota?

> **Status of these notes.** Google does not publicly document the server-side
> gate for the Photos backup perk, so the "what Google checks" and
> "what happens on revocation" sections are **inferences from public sources
> (AOSP, Play Integrity docs, Google storage-policy pages) plus first-hand
> community reports**, not Google statements. Each section flags its confidence.
> Treat any "it works" detail as version-pinned and perishable — this is a
> fast-moving cat-and-mouse area.

---

## TL;DR

- **Shizuku alone: no.** Shizuku is a Binder/IPC privilege bridge (ADB-shell
  UID 2000, or root UID 0), *not* a code-injection framework. It cannot run
  code inside the Photos process, and it cannot per-app override `Build.*` or
  `hasSystemFeature`. PixelMask fundamentally needs in-process injection.
- **LSPatch is the real "Shizuku など" path** (it uses Shizuku only to *install*
  the patched APK). It can run PixelMask's exact hooks in-process **but
  re-signs the Photos APK**, which breaks Google-account sign-in against real
  Play Services → the perk can never be claimed. **Dead end for Google Photos.**
- **The only no-root path that actually works is the ReVanced model**
  (re-signed + *renamed* Photos package logging in through **microG/GmsCore**,
  which doesn't enforce Google's first-party signature). That is a *different
  architecture*, not a tweak to this module, and it is fragile.
- **The perk gate today is the software device-API claim** (`Build.*` +
  `hasSystemFeature`), **not** hardware key attestation and **not** a Play
  Integrity verdict. Proof: a rooted device categorically *fails* hardware
  attestation, yet the root+LSPosed spoof still works.
- **PixelMask is "more legitimate" than ReVanced only on the app-signature and
  GMS axes** — but the perk doesn't gate on those axes today, so that advantage
  doesn't currently help the perk light up; meanwhile root adds its *own*
  detectable signal (failed device integrity). The one axis that actually gates
  the perk (the software device claim) is identical for both.
- **Revocation is go-forward.** Already-uploaded "free" photos keep their exempt
  tag; only **new** uploads start counting. There is **no documented retroactive
  per-photo re-count** for the device/quality exemption (which is what PixelMask
  grants). The real tail risk is **account-level ToS action**, not per-photo
  re-metering.

---

## 1. Can PixelMask run without root?

PixelMask is a standard Xposed module (YukiHookAPI over the classic
`de.robv.android.xposed` API). Inside the Photos process it overrides
`android.os.Build.*` static fields (`setStaticObjectField`) and hooks
`ApplicationPackageManager.hasSystemFeature`. **Both require running code inside
the Google Photos process.** Everything below is about whether that injection
can happen without root.

### Shizuku alone — No (architectural, not a privilege-level limit)

Shizuku runs a privileged Java process (via `app_process`) with the identity of
the ADB shell user (UID 2000) or root (UID 0) and acts as a **Binder middle-man**
that forwards an app's requests to `system_server`. That is an IPC privilege
*bridge*, not a hooking/injection mechanism. Consequently it **cannot**:

- inject or hook code inside another running app's process (no ptrace/zygote
  path; shell-UID can't ptrace a foreign higher-privileged app). Even Shizuku's
  "User Service" runs in a *separate* process, never inside the target app.
- override `Build.*` for a single app — those derive from read-only
  `ro.product.*` props baked at zygote fork and read in-process.
- make `hasSystemFeature` return `true` for a missing feature, scoped to one app
  — system features come from the global `SystemConfig` parse of
  `/system|/vendor/etc/permissions/*.xml`; there is no per-app override.

What shell-UID *can* do (`pm install`/`grant`, `appops`, `settings put`, `am`,
`cmd <service>`) is real but **global/declarative** — none of it rewrites the
in-memory values a target app reads. *Confidence: high.*

### LSPatch — injects in-process, but re-signing kills Google login

LSPatch ("rootless LSPosed") repackages the target APK, inserting the LSPosed
loader dex + `.so` so module hooks run inside the patched app's own process. Its
**manager/integrated mode uses Shizuku to install** the patched APK with system
privileges — this is what people mean by "Shizuku など". PixelMask's hooks are
standard Xposed API, so they are *API-compatible* in principle (YukiHookAPI lists
LSPatch as only partially supported, vs. stable for LSPosed).

**The blocker is signing.** LSPatch re-signs Photos with a non-Google key,
changing the certificate SHA-1. Google's OAuth / Play Services bind account
tokens to *(package name + signing-cert SHA-1)* and verify the first-party
signature server-side, so a re-signed Google Photos **cannot log into a Google
account against real Play Services**. No login → no backup → no perk, so the
spoof becomes moot. Documented for other Google apps (e.g. GBoard, LSPatch
issue #218) and for Pixelify specifically (BaltiApps issue #13: "LSPatched
Photos would not pass Google server authentication"). *Confidence: high.*

### App-cloning / VirtualXposed — No / broken

Pure clone & work-profile tools (Island, Shelter, dual-app/parallel-space)
*isolate* an app but do **not** inject/hook, so they can't spoof `Build` at all.
VirtualApp-based in-sandbox Xposed (VirtualXposed, etc.) is largely
unmaintained, officially Android ≤10, and hits the same GMS-login wall inside
the container. Not viable. *Confidence: high.*

### The only working no-root path: ReVanced + microG

The one no-root stack reported to actually grant the perk
(e.g. `mentalblank/GPhotos-Revanced`, actively built into 2026):

1. Patch a **standalone, renamed** Photos APK with a "spoof Pixel features"
   patch (so it doesn't overwrite / collide with the signed original), and
2. Authenticate via **microG / GmsCore** instead of real Play Services — microG
   doesn't enforce Google's first-party signature allowlist, so the re-signed
   app can sign in.

This is a **different architecture**, not "PixelMask made rootless," and it is
fragile: degraded microG login UX (re-select account each launch, logged-out on
reboot), flaky / broken background sync on newer targets (microG #3261 on
Android 16 Pixel; ReVanced #5752 on Pixel 10), the ReVanced patches repo being
DMCA-blocked (Mar 2026, builds moved to mirrors), and cases where the re-signed
package isn't on microG's extended-access allowlist so auth is denied
(ReVanced #6453). *Confidence: high on the mechanism; "works" is perishable.*

**Recommendation:** keep root + LSPosed as the primary path. If a no-root
offering is ever wanted, it is a separate ReVanced/microG-style fork (with
permanent microG-version maintenance), not a change to this module. The
pragmatic move is to point no-root users at an existing ReVanced GPhotos build.

---

## 2. What Google actually checks (PixelMask vs ReVanced detectability)

### The perk gate is the software device claim — not hardware attestation

The unlimited Original-quality perk is granted on the **self-reported device
identity** (`Build.*` + `hasSystemFeature` flags) for the grandfathered
**2016 Pixel** profile (codename `sailfish` / `marlin`). It is **not** gated on
hardware key attestation (TEE/StrongBox) and **not** on a Play Integrity
`STRONG`/`DEVICE` verdict.

**The decisive proof:** a rooted, bootloader-unlocked device *categorically
fails* hardware-backed Play Integrity — yet the root+LSPosed spoof grants the
perk. If the perk consumed hardware attestation, no rooted spoof could ever
work. So, at the granting layer, **a spoofed Pixel is indistinguishable from a
real one**, because the only thing checked is the value the spoof controls.
*Confidence: high (this is an inference from observed behavior, but a very
strong one).*

> Only the original 2016 Pixel / Pixel XL has lifetime unlimited **Original**
> quality. Pixel 2–5 are Storage-Saver only. Spoofing a "Pixel 9" never grants
> it — real Pixel 9s have no such perk. Many "stopped working on Pixel 9"
> reports are this misattribution (wrong spoof *target*, not host hardware).

### Why spoofs "expire" (the "revoked within hours" reports)

The perishability is **not** hardware detection. The reproducible causes are:

- **Client-side app-version gating** — works on Photos ~v6.60–6.65, breaks on
  v6.66+ / Android 14+; **downgrading the Photos APK restores it**. A hardware
  /attestation block could not be cured by an APK downgrade — this is the clean
  tell that it's client logic.
- **Server-side re-validation of the *claimed* device profile** — a clean
  re-spoof of the 2016-Pixel profile re-grants it (consistent with re-checking a
  rewritable claim, not a TEE-rooted proof).
- **Newest-hardware tightening (watch this):** the *same* ReVanced APK grants
  the perk on a Pixel 8 Pro but not a Pixel 10 Pro, both Android 16
  (ReVanced #5752). Identical app + identical spoof, different real hardware →
  different result. That is a sign Google is *beginning* to cross-reference the
  real device server-side on the newest generations.

### PixelMask vs ReVanced — axis by axis

| Axis | PixelMask (root + LSPosed) | ReVanced (re-sign + microG) | Does the perk gate read it *today*? |
|---|---|---|---|
| App legitimacy (signature / package / GMS) | **Genuine** — not even spoofed, it *is* the Google-signed app on real Play Services | Non-genuine — re-signed, renamed, microG | **No** |
| Device integrity (root state) | **Abnormal** — rooted, fails Play Integrity unless hidden (TrickyStore/Shamiko) | **Normal** — non-root, locked bootloader, can pass device integrity | **No** |
| Device identity (`Build` + features) | Software spoof | Software spoof (same) | **Yes — only this** |
| Hardware key attestation (TEE) | Cannot forge | Cannot forge | **No** |

So the user intuition "PixelMask looks like a legitimate app, ReVanced doesn't"
is **correct on the app-signature/GMS axes** — and PixelMask isn't "spoofing"
legitimacy there, it genuinely *is* the real app. **But** the perk gate does not
currently read those axes, so that advantage doesn't change whether the perk
lights up today; both work on compatible setups and both break on newest-gen
hardware. PixelMask's legitimacy edge would only start to matter **if Google
wires app-signature / GMS authenticity into the perk gate** — at which point
PixelMask survives and ReVanced doesn't. Conversely, root is PixelMask's *own*
abnormal signal on the integrity axis. *Confidence: high on the axis
decomposition; medium on "the gate ignores these axes," since Google's logic is
undocumented.*

### The hardware ceiling (true for both)

No software `Build`/`hasSystemFeature` hook — LSPosed *or* ReVanced — can defeat
**hardware-backed key attestation**: the Android Keystore attestation
certificate reports the real `attestationIdBrand/Device/Model` and
`rootOfTrust` (`verifiedBootState`, `deviceLocked`) straight from the TEE,
signed by a Google-rooted key, untouched by `Build` hooks. **At this layer
PixelMask has zero advantage over ReVanced** — neither can forge a genuine Pixel
attestation. The only known boot-state forgery is keybox spoofing (TrickyStore
replaying a *leaked* attestation key), which is a separate, revocation-prone
toolchain — not part of PixelMask. **If Google ever moves the perk gate to
hardware attestation, both paths break identically.** *Confidence: high.*

---

## 3. What happens to already-backed-up photos when the perk is revoked?

**Bottom line: for the Pixel device / quality exemption (what PixelMask grants),
already-uploaded "free" photos are NOT retroactively re-counted. The exempt flag
is set per-item at upload time and is go-forward-only.** When the spoof breaks,
only **new** uploads start consuming the 15 GB; the existing free library stays
free. *Confidence: high for documented policy; medium for the spoof case (thin,
anecdotal evidence + architectural inference).*

Evidence:

- **2021-06-01 High-quality change.** Google, verbatim: photos backed up in High
  quality *before* June 1, 2021 "will not count toward your 15 GB" and "will
  still be considered free and exempt from the storage limit." Only new uploads
  count. Grandfathered, not re-counted.
- **Genuine Pixel per-device cutoffs** (Pixel 2 ended ~Jan 2021, Pixel 3 ended
  ~Jan 2022): only future uploads were affected; pre-cutoff uploads stayed free.
- **Spoof case.** Pixelify #254: when the spoof stops, "automatic backup starts
  consuming storage from my Google Drive 15 GB" — i.e. **new** uploads. No report
  of old ones being re-counted. Pixelify #333: a user uploaded ~400 GB via the
  spoof and saw no clawback or ban 1–2 years later — direct evidence against
  retroactive clawback.

### Four caveats

1. **"Shows unlimited but eats storage" ≠ retroactive re-count.** That is
   actually (a) the spoof having *already broken at the moment those items
   uploaded*, so the server counted them on arrival (an upload-time decision,
   not a re-count of old photos), combined with (b) a **stale local app UI**
   still showing a cached "unlimited" banner. **Server accounting is
   authoritative; the app display lags.**
2. **Editing an old free photo re-uploads it** as a new version, which then
   counts. User-triggered, not a Google-initiated re-count.
3. **The one mechanism that DOES retroactively re-count is plan-level
   entitlements** (e.g. a Google One "unlimited photos" add-on): when that plan
   ends, "existing photos that were previously exempt will begin counting
   again." **This is a different mechanism from the Pixel device/quality
   exemption** — PixelMask's spoofed perk is the per-upload quality type, *not*
   the plan type, so this re-count does not apply to it. Don't conflate them.
4. **The real tail risk is account-level, not per-photo.** Spoofing device
   identity to obtain a paid perk violates Google's ToS. The lever Google would
   pull is not per-item re-metering but **account suspension/ban** — a clawback
   of the *entire* library. No confirmed first-hand ban report specific to the
   Photos spoof was found (possible survivorship bias), but the ToS violation is
   real.

**User-facing guidance:** the perk is perishable (it will eventually stop
applying to new uploads), but **already-backed-up photos are not retroactively
billed or deleted** for the device-perk type. The worst-case risk is a
(so-far-unobserved) account action — so **keep an independent local/secondary
backup of anything important**. Worth a line in the README disclaimer.

---

## Confidence & sources

- **Documented (high):** Shizuku's architecture (RikkaApps README / Shizuku-API
  docs); AOSP `Build`/`SystemConfig`/Keystore-attestation mechanics; Play
  Integrity verdict definitions; Google's 2021 storage-policy pages
  (`blog.google`, `support.google.com/photos/answer/10100180`).
- **Strong inference (high→medium):** "the perk gate reads the software device
  claim, not hardware attestation" (proven by rooted-spoof-works-despite-failing
  -attestation); "revocation is go-forward for the device/quality exemption."
- **Anecdotal (medium→low):** the no-ban / no-clawback spoof outcomes
  (Pixelify #254, #333), the Pixel-9/10 server-side tightening reports (some
  XDA pages were unreachable; relied on secondary summaries). Treat as
  directional, not definitive.

These notes were produced by a multi-agent research + adversarial-verification
pass; claims that survived a refutation attempt are the ones stated above.
