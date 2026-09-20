# ShareSafe

**Screenshot → detect sensitive data → redact → preview → Safe Share.** No backend, no login, no
paid API, no analytics. The scan, the redaction and the verification never touch the network; the
only network use in the app is the ad slot, and it is never handed an image, a file name or a single
detection result.

ShareSafe is an independent Android app built after auditing [SysAdminDoc/SnapCrop](https://github.com/SysAdminDoc/SnapCrop)
for its on-device approach. The useful ideas (offline OCR, box-based redaction, crop-away system
bars) were re-implemented on a Kotlin + Jetpack Compose foundation; none of its branding, assets,
package name, or code was copied.

---

## Status

Verified on-device on Android 16 (API 36):

- 96 JVM unit tests, all passing (`:app:testDebugUnitTest`)
- 22 instrumented tests on a real emulator (`:app:connectedDebugAndroidTest`), including the
  one-tap automatic path, the calendar history, the language switch, the whole Batch Protect run,
  the long-screenshot memory budget, and the assertion that the two screens showing the user's own
  pixels carry no ad slot at all
- The minified release build (`assembleRelease`, R8 on) was driven by hand on the emulator: it
  installs over the previous release with `adb install -r` → opening a shared screenshot lands in
  the editor with **3 sensitive areas found** and the chat-layout row present → *Preview & share* →
  **"Verified clean: no readable sensitive data left in the exported image. Checked text, QR codes
  and faces."** → *Save to gallery* writes a new file to `Pictures/ShareSafe`, with no crash logged
- Android Lint: `lintRelease` passes with **no errors**; the remaining warnings are all style
  suggestions (`UseKtx`, plural candidates, pinned dependency versions)

## The core flow

1. **Select** — system photo picker (`PickVisualMedia`, so no gallery permission is needed), a
   recent screenshot from the in-app strip, or a screenshot shared in from another app. There is
   one primary action on the home screen; picking *is* the automatic path.
2. **Detect** — one pass over a downscaled bitmap: ML Kit OCR (bundled Latin model), regex
   heuristics, ML Kit barcode/QR, ML Kit face detection, all in parallel on `Dispatchers.Default`.
   Text boxes are published while the barcode and face passes are still running.
3. **Redact** — every hit arrives switched on; tap a box to leave it alone, or drag anywhere to add
   a region of your own. Styles: **blur**, **pixelate**, **black bar**, **tint** (with a strength
   slider), plus undo/redo and reset.
4. **Preview** — the exported bitmap is re-scanned, missed areas are **repaired automatically**, and
   the repair is re-verified (up to two rounds) before the share sheet is offered.
5. **Share** — *Safe Share* hands the permanent PNG to WhatsApp/Telegram/any app through
   `FileProvider` + the system chooser, *Save to gallery* writes it to `Pictures/ShareSafe`, and
   either action records the export in History.

## Automatic by default

The app is built so the safe outcome is the default, not a reward for careful manual work:

- **Scan on open.** Opening an image starts detection immediately (`autoScan`).
- **Everything found is hidden.** Detections start enabled (`autoSelectAll`), so the editor is used
  to *remove* boxes rather than to place them. “Hide everything found” in the overflow menu turns
  every category back on in one tap.
- **One action, one journey.** The home screen used to offer “pick a screenshot” and “do it for
  me” side by side, which were two names for the same trip. The automatic pipeline is now the only
  path: the big card picks an image, a thumbnail tap protects that screenshot immediately, and both
  run scan → redact → crop → render → verify → repair and land on the finished preview. The manual
  editor is still there, one tap behind “Keep editing”, for the minority of images that need it.
- **Verify, then repair.** Verification returns *where* it still sees something; `AutoFixPlanner`
  maps those rectangles from exported space back through beautify padding and crop into source space,
  the renderer hides them, and verification runs again (max two rounds). Anything the user switched
  off is excluded from both the verdict and the repair.
- **Batch, one image at a time.** Long-press to select several screenshots and redact them all to
  `Pictures/ShareSafe`, each recorded in History. A batch runs **sequentially on purpose**: every
  image is decoded, scanned, redacted, verified, repaired and written before the next one is opened,
  so peak memory is one full-resolution bitmap plus a handful of 320 px thumbnails regardless of how
  many images were selected. Each run goes through the same verify-and-repair loop as a single
  export, which is what makes “a batch is not less safe” a fact instead of a hope.
- **Chat Privacy Mode.** A screenshot from WhatsApp, Telegram or a generic DM can be tagged with its
  layout, which adds the conversation-header name and the profile pictures as ordinary detections —
  switched on, individually toggleable, and correctable by hand before export. The presets are
  suggested automatically when the image looks like a conversation, never applied silently.

## Feature set

| Area | Implemented |
| --- | --- |
| Input | System photo picker, recent-screenshot strip, batch multi-select (**Batch Protect** screen with per-row progress, per-item preview, retry and share-all), incoming `ACTION_SEND` share target |
| Detection | Phone numbers (ID + international), email, card numbers (Luhn-checked), NIK, NPWP, passport (keyword-gated), account numbers and IBAN, OTP codes (found by context), leaked secrets (cloud/API keys, JWTs, private-key headers, bearer tokens, `password:`/WiFi passphrase assignments), network addresses (IPv4/IPv6/MAC), licence plates, keyword-gated street addresses (opt-in), optional long digit runs, QR/barcode payloads (QRIS payments, 2FA secrets, WiFi, contacts, payment and social links), face boxes |
| Redaction | Blur, pixelate, black bar, tint; adjustable strength; per-detection toggle; drag-to-add and drag-to-move regions; undo/redo/reset; automatic repair of verified leftovers; faces masked as boxes or soft ovals ("hide the boxes on faces") |
| Chat privacy | Chat Privacy Mode for WhatsApp / Telegram / DM layouts: header name + profile picture added as toggleable detections, suggested automatically from the image |
| Cropping | Auto-trim status bar + navigation bar, optional trim of blank edges, all on one normalized geometry model |
| Beautify | Padding, rounded corners, drop shadow, and premium presets — **Clean, Night, Solid, Aurora, Studio** — each a fixed combination of padding, corner radius, shadow and background (solid or gradient). Auto/pick-your-own background still available; the two heaviest presets (Aurora, Studio) are unlocked by one rewarded video, while the other four are free and stay free |
| Export | Permanent bake-in (never a layer), PNG/JPEG with adjustable JPEG quality, size caps (original/1080/720), save to gallery, Safe Share sheet, direct WhatsApp/Telegram hand-off, optional auto-save on export |
| History | Calendar view of every export with day dots, per-day list, thumbnails, stats (exports, areas hidden, active days, day streak), re-share, **re-open in the editor** and delete; stores only the redacted copy, never the original, and never leaves the device |
| UX | Animated splash, four-page animated onboarding (replayable from About), single-action home with trust chips + stats + recents strip, gesture canvas (pan + pinch zoom), Material 3 theme with five palettes + Material You + light/dark/system, **English by default** (with Bahasa Indonesia and "device language" one tap away), animations and haptics toggles, About screen with rate/share/licences, Settings as collapsible top-down cards |
| Ads | Google Mobile Ads, configured with Google's **test** application id and test ad units only: a banner on chrome screens (Home, History, Settings, About, Batch — never on the editor or the preview), an interstitial rationed by a pure policy (from the 3rd share, at most once every 2 minutes, and never while an edit is in progress), and an optional rewarded video that lifts the free five-image batch limit and removes all ads for 24 h |

## Privacy model

The privacy story changed when advertising was added, and the honest version is more specific than
"nothing leaves your device":

- **No upload path exists in the app.** There is no code that sends an image, a detection, an OCR
  string or a file name anywhere. The scan, the redaction and the verification are pure on-device
  code operating on in-memory bitmaps.
- **The only network use is an ad request, and it is structurally blind.** The ads SDK is asked for
  a format — banner, interstitial, rewarded — and given nothing else. There is no call site where
  the SDK is passed a bitmap, a path or a string from a scan; the banner is a single `AdView` with
  no targeting parameters, and no ad slot exists on the two screens that render the user's pixels.
- **The network permissions are audited, not assumed.** `PermissionPolicyInstrumentedTest` reads the
  merged manifest of the *installed* APK and fails if anything outside a reviewed allow-list
  appears, if the network permissions are anything other than `INTERNET` +
  `ACCESS_NETWORK_STATE`, or if any capability that could read the rest of the phone (camera,
  contacts, location, SMS, microphone, `QUERY_ALL_PACKAGES`, …) shows up. ML Kit's transitive
  `datatransport` telemetry library rides along inside that dependency graph, so the test is doing
  real work rather than restating an intention.
- **Bundled models.** `com.google.mlkit:text-recognition` / `barcode-scanning` / `face-detection`
  ship their models in the APK, so a scan works with the radio off (that is also why the APK is
  large — see *Artifacts*).
- **No backups.** `allowBackup=false`, `dataExtractionRules` opt-out, redacted output written only
  where the user asks.
- **No analytics, no crash reporting, no accounts.** The ad SDK is the one third-party runtime
  dependency that talks to the network, and it can be disabled by flipping `AdsConfig.ENABLED` to
  `false` — no call site changes.
- **Ads are rationed, and one policy file decides.** `InterstitialPolicy` (pure, unit-tested) allows
  an interstitial only from the third share onward, at most once every two minutes; the banner never
  appears on the editor or the preview — the two screens that render the user's own pixels; a
  rewarded video removes all ads for 24 hours and lifts the free five-image batch limit. **No
  redaction capability is ever behind an ad**: the batch limit is a shortcut, not a wall, because the
  same images can always be selected again in smaller groups, and every detection, style, preset and
  verification pass stays available in the free tier.

## Permissions & Google Play checklist

| Permission | Why | Notes |
| --- | --- | --- |
| `READ_MEDIA_IMAGES` | List recent screenshots in-app (API 33+) | Photo-picker flow itself needs none |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Honour Android 14+ "Select photos" partial grant | Treated as usable access, not as denial |
| `READ_EXTERNAL_STORAGE` (`maxSdkVersion=32`) | Same listing on older devices | Never requested on API 33+ |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Loading ads (Google Mobile Ads declares these itself; they are listed explicitly in the manifest so a reviewer reads the intent) | Nothing else in the app uses the network |
| `com.google.android.gms.permission.AD_ID` | Advertising ID, declared explicitly so Play Console *Data safety* matches the manifest | Play services provides it; users can reset/limit it in system settings |
| `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_TOPICS` | Privacy Sandbox permissions the ads SDK declares on Android 14+ | Merged from the ads library |
| `WAKE_LOCK`, `FOREGROUND_SERVICE` | Footprint of `androidx.work:work-runtime:2.7.0`, which `play-services-ads` pulls in transitively | The app never enqueues work; no service in the merged manifest declares a `foregroundServiceType`, and `PermissionPolicyInstrumentedTest` asserts that on device |
| `com.sharesafe.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Signature-level permission androidx.core declares for its own receivers | Not user-visible |

Play-readiness notes:

- **Photo and video permissions policy** — the app reads images only, to redact them, at the user's
  explicit action; the system picker path works without any permission, so the declared permission
  is a convenience for the "recent screenshots" strip and should be justified with a short
  screen recording in the declaration form.
- **Data safety** — the ads SDK does collect device and advertising identifiers, so the declaration
  is *not* "no data collected". Declare: advertising ID and device identifiers collected by Google
  Mobile Ads for advertising, and "photos" are processed on-device and never shared. The app itself
  collects nothing.
- **Ads policy** — Google Mobile Ads is used with the banner, interstitial and rewarded formats and
  Google's official **test** ids (`core/ads/AdsConfig.kt` + the `APPLICATION_ID` meta-data). Replace
  those five constants with production values before publishing. No ad is shown on the editor or the
  preview, and no interstitial appears before the first two shares.
- **Sensitive APIs** — no `QUERY_ALL_PACKAGES` (only three explicit `<queries>` packages plus the
  share intent for hand-off buttons); no accessibility or device-admin usage; foreground service
  not required.
- **Families / ad content rating** — the tool can be used on any screenshot, so the rating should be
  set from the actual questions answered, not from this README.
- **Signing** — the release build currently reuses the debug key so it stays installable from a
  fresh checkout. Replace `signingConfigs.getByName("debug")` in `app/build.gradle.kts` with a real
  upload key before publishing.

## Build & run

```bash
# debug build + unit tests
./gradlew :app:assembleDebug :app:testDebugUnitTest

# per-ABI release APKs (splits are disabled automatically when a bundle task is in the list,
# because AGP rejects splits + bundle in one invocation)
./gradlew :app:assembleRelease

# Play upload artifact
./gradlew :app:bundleRelease

# on-device suite (needs a running emulator/device)
./gradlew :app:connectedDebugAndroidTest
```

Toolchain: Gradle 8.14.3, AGP 8.13.1, Kotlin 2.2.20, Compose BOM 2025.10.01, Java 17,
`minSdk 29`, `compileSdk`/`targetSdk 36`. Versions are pinned in `gradle/libs.versions.toml`.

### Artifacts

Everything below is v1.3.0 (`versionCode 4`).

| Output | Size |
| --- | --- |
| `app-arm64-v8a-release.apk` | 37.8 MB |
| `app-armeabi-v7a-release.apk` | 29.1 MB |
| `app-x86_64-release.apk` | 40.4 MB |
| `app-universal-release.apk` | 104.6 MB |
| `app-release.aab` | 51.8 MB |

Each release tag carries all four artifacts for direct download, e.g.
[`v1.2.0`](https://github.com/fareza777/share-safe/releases/tag/v1.2.0) — use the `arm64-v8a` APK on
any phone from 2016 or later, the `universal` APK if you are unsure, and the `.aab` for Play.

Both release artifacts are signed with the **debug key on purpose**, so the APK in this repository
can be installed over an earlier build from the same source (`adb install -r …`) instead of forcing
an uninstall. Replace `signingConfigs.getByName("debug")` in `app/build.gradle.kts` with a real
upload key before shipping to Play.

Note for the build commands below: run `assembleRelease` and `bundleRelease` in **separate** Gradle
invocations if you want the per-ABI APKs, because the build disables splits whenever a bundle task is
part of the same run (AGP rejects splits + bundle in one invocation).

Most of the weight is the three bundled ML Kit models plus their native libraries; the AAB is what
Play should receive, since it splits by ABI per device.

## Architecture

```
app/src/main/java/com/sharesafe/app/
├── core/
│   ├── model/        IntRect / NormRect / Detection / RedactionStyle / BeautifyConfig
│   ├── detect/       SensitivePatterns (pure regex), DetectionMapper, SensitiveScanner,
│   │                 OcrEngine, CodeEngine, CodePayloadClassifier, FaceEngine, MlKitAwait,
│   │                 ChatHeuristics (pure chat-layout geometry + preset suggestion)
│   ├── render/       RenderPipeline (the only place pixels are produced), RedactionRenderer,
│   │                 BeautifyRenderer, AutoTrim, Bitmaps
│   ├── image/        ScreenshotRepository (MediaStore), BitmapLoader (source cap + thumbnails)
│   ├── batch/        BatchProtectEngine (one image at a time, verified, memory-bounded),
│   │                 BatchRedactor (the older multi-image gallery pass)
│   ├── verify/       SecureRender (render → verify → repair → verify, shared by editor and batch),
│   │                 RedactionVerifier (post-export re-scan) + AutoFixPlanner (leftover → source)
│   ├── history/      HistoryEntry / HistoryCodec / HistoryCalendar (pure, unit-tested)
│   ├── ads/          AdsConfig (test inventory + master switch), InterstitialPolicy (pure)
│   └── export/       ImageExporter (MediaStore write), ExportOptions, ShareHelper
├── ui/               ShareSafeRoot (animated navigation), splash/, onboarding/, home/, editor/
│                     (ViewModel + gesture canvas), preview/, batch/ (Batch Protect),
│                     history/, settings/, about/, LocaleSupport, components/ (Motion: counters,
│                     shimmer, appear-in; AdSlots: banner / interstitial / rewarded wrappers;
│                     PlusUnlock: the shared rewarded-unlock dialog), theme/ (5 palettes)
└── data/             SettingsStore, HistoryStore
```

Design rules worth keeping:

- All geometry is normalized (0..1) in the model and converted to pixels in exactly one place, so
  preview, export, and verification can never disagree.
- `SensitivePatterns`, `DetectionMapper`, `AutoTrim`, `HistoryCalendar`, `ChatHeuristics`,
  `SensitiveScanner`'s scoring, `BeautifyPreset` and `InterstitialPolicy` are pure Kotlin (no Android
  types, or in the scanner's case no Android calls) and are what the JVM unit tests target. The rule
  that decides when a user may be interrupted by an ad is testable precisely because it is not a
  callback; the rule that decides where a conversation's name sits is testable because it is geometry
  rather than a vision model.
- `SecureRender` is the single implementation of render → verify → repair → verify. The editor's
  export and Batch Protect both call it, so "the batch is verified too" is enforced by there being
  only one loop, not by remembering to copy it.
- Memory is bounded by construction, not by luck: `BitmapLoader.MAX_SOURCE_DIM` caps every decode
  (a 1080×7200 long screenshot is downscaled, not decoded at native size), the batch engine holds
  exactly one decoded image (`MAX_CONCURRENT_IMAGES = 1`) and the UI holds only 320 px thumbnails.
- Ads live behind `AdsConfig.ENABLED` and three slot wrappers; no composable builds an `AdView` or
  issues a `load()` call itself, so "where may an ad appear" is answerable by reading one file.
- The render pipeline always works on its own copy of the source bitmap — redaction can never
  mutate the image the user opened, so toggling a region off restores the original pixels.
- UI side effects (Toasts, share sheets) are dispatched through `UiActions` to stay on the main
  looper regardless of which coroutine finishes first.

## Testing

| Suite | Covers |
| --- | --- |
| `SensitivePatternsTest`, `ExtraHeuristicsTest`, `DetectionMapperTest`, `AutoTrimTest`, `ModelGeometryTest`, `CodePayloadClassifierTest`, `HistoryCodecTest`, `AutoFixPlannerTest`, `InterstitialPolicyTest`, `ChatPrivacyModeTest`, `BeautifyPresetTest` (96 tests) | Regex heuristics (including network addresses, plates, addresses, IBAN), QR payload classification, normalized↔pixel geometry, auto-trim, the history line format and calendar maths, the leftover→source mapping the automatic repair depends on, the ad-frequency policy and the free batch limit, chat-layout geometry and preset suggestion, the beautify presets and the face-mask styles, and a guard that every ad unit id is still Google's test inventory |
| `RedactionPipelineInstrumentedTest` | Real ML Kit OCR/barcode/face detection on a synthetic screenshot, redaction styles, crop + beautify path, export, and the verifier |
| `FullFlowUiTest` | The single primary action, one-tap protect → preview → keep editing → editor → preview → save through the actual Compose UI, plus the assertion that the editor and preview carry no ad slot |
| `AutoHistoryUiTest` | The one-tap path end to end on device: verify → repair → save → History shows the entry |
| `ThemeSwitchInstrumentedTest` | Every palette applies without leaving Settings, About is reachable through its collapsed card, and switching the language to Indonesian relabels the running Activity |
| `PermissionPolicyInstrumentedTest` | The merged manifest of the installed APK declares nothing outside a reviewed allow-list, uses exactly the two network permissions ads need, and requests nothing that could read the rest of the phone |
| `AppLaunchInstrumentedTest` | Cold start does not crash (application init, theme resolution, first composition) |
| `BatchProtectInstrumentedTest` | Multi-select → Batch Protect → every row terminal → summary through the real UI; then the engine itself on three seeded images including a 1080×7200 long screenshot: all finished, all written to the gallery, thumbnails inside the 320 px cap, and heap growth across the run inside a stated ceiling. Also pins that a tall screenshot is decoded inside `MAX_SOURCE_DIM` and that the engine still touches **one** image at a time |

Dark mode was checked objectively (mean screen luminance 89 vs 231 in light) and the release build
was smoke-tested for the whole flow, so the R8 rules and permission stripping are known-good on
device, not just at compile time.

## Known limits / next steps

- OCR quality depends on the bundled Latin model; non-Latin scripts (CJK, Arabic) are not covered by
  this model and would need extra recognizers (and more APK weight).
- Faces are detected and pre-selected like any other kind, so a portrait gets redacted by default;
  the "Face" chip in the editor deselects them all in one tap when the faces are the point of the
  screenshot.
- The release build is debug-signed (see above) so it can be installed over an older build. A real
  upload key, a Play listing, and the photo-permission declaration video are the remaining
  publishing steps.
- Advertising uses Google's test app id and test ad units. Real impressions need a real AdMob
  account, and Play additionally wants a privacy-policy URL once the ads SDK is live.
- Screenshot detection relies on `MediaStore` screenshot buckets; on OEMs that park screenshots
  outside them, the system picker is the reliable path.
- Automatic repair runs at most two rounds and only fixes what the verifier can still read; a
  redaction that a recognizer cannot see (for example very small blurred text) will not be found by
  either pass.
- History stores the redacted copies capped at 120 entries and a 1440 px long edge, so it cannot
  grow without bound.
- **Chat Privacy Mode is geometric, and the README says so.** It hides the conversation *header*
  (name band + avatar) and adds profile pictures, because those are the parts a layout determines.
  Sender names *inside* message bubbles are deliberately not attempted: picking a person's name out
  of a message body needs name recognition, and a guess would either miss it or eat the message. The
  phone numbers, e-mails, codes and links in those bubbles are covered by the ordinary patterns, and
  the manual editor is one tap away for the rest — which is why every chat detection arrives as an
  ordinary, individually toggleable box rather than a black box the user has to trust.
- Batch Protect holds no full-resolution bitmap in the UI layer: the engine writes each result to the
  gallery and drops it before opening the next, so the progress screen is thumbnails only. The cost
  is that a batch cannot be re-rendered from memory — re-running one means selecting the images
  again, which is the same reason the limit is a shortcut rather than a wall.

## Ringkasan (ID)

ShareSafe memproses semuanya di perangkat: pilih screenshot, deteksi otomatis (nomor HP, email,
nomor kartu, NIK/NPWP/paspor, OTP, token, koordinat, alamat jaringan, plat nomor, QR/barcode, wajah),
redaksi dengan blur/pixelate/black bar/tint, potong status & navigation bar, rapikan (padding +
sudut membulat), lalu **Safe Share**. Tidak ada backend, login, atau API berbayar.

Pemindaian, penyensoran, dan verifikasi **tidak pernah menyentuh jaringan**. Satu-satunya pemakaian
jaringan adalah slot iklan Google Mobile Ads, dan permintaan iklan tidak pernah menerima gambar,
nama berkas, atau hasil deteksi apa pun. Iklan hanya muncul di halaman aplikasi (Beranda, Riwayat,
Pengaturan, Tentang) — tidak pernah di editor atau pratinjau — dan bisa dimatikan 24 jam dengan
menonton satu video (rewarded). Semuanya masih memakai **test ID resmi Google**.

Alurnya otomatis secara bawaan dan sekarang hanya ada **satu aksi utama** di layar utama: pilih
screenshot, lalu pindai → sensor → potong → render → verifikasi → perbaikan berjalan sendiri dan
langsung mendarat di pratinjau. Ketuk thumbnail di daftar “Screenshot terbaru” untuk hasil yang sama
pada gambar itu, atau tekan lama untuk memilih beberapa sekaligus (batch). Editor manual tetap ada,
satu ketukan di balik tombol “Lanjut mengedit” di pratinjau.

Tahap kedua menambahkan beberapa hal yang selama ini harus dikerjakan manual:

- **Chat Privacy Mode** untuk screenshot WhatsApp/Telegram/DM: nama di header percakapan dan foto
  profil ikut tersensor otomatis, mode-nya bahkan disarankan sendiri saat gambar terlihat seperti
  percakapan. Semua hasilnya tetap kotak biasa yang bisa dimatikan atau digeser satu per satu.
- **Batch Protect**: pilih beberapa screenshot, lalu satu per satu dipindai, disensor, diverifikasi,
  diperbaiki dan disimpan — **satu gambar dalam satu waktu**, jadi pemakaian memori tidak tumbuh
  mengikuti jumlah gambar (screenshot panjang 1080×7200 pun diturunkan resolusinya sebelum diproses).
  Setiap baris punya progres sendiri, bisa dibuka pratinjaunya, diulang bila gagal, dan semuanya bisa
  dibagikan sekaligus. Lima gambar pertama gratis (grup lebih besar bisa dibuka dengan satu video).
- **Beautify premium**: shadow, latar solid/gradien, dan lima preset siap pakai (Clean, Night, Solid,
  Aurora, Studio) — bukan editor foto, hanya pembingkai.
- **Sensor wajah tanpa kotak**: pilihan masker oval lembut supaya sensor wajah tidak terlihat seperti
  stiker “disensor”, termasuk bisa dijadikan bawaan di Pengaturan.
- **Riwayat bisa dibuka ulang** langsung di editor, selain dibagikan ulang dan dihapus; semua salinan
  sensor disimpan lokal di perangkat.

Kebijakan iklannya juga lebih ketat: interstitial baru muncul mulai ekspor **ketiga** dan maksimal
sekali per dua menit, banner tidak pernah ada di editor maupun pratinjau (dua layar yang menampilkan
piksel milik pengguna), dan **tidak ada kemampuan penyensoran yang dikunci iklan** — batas batch
adalah jalan pintas, bukan tembok, karena gambar yang sama selalu bisa dipilih ulang dalam grup kecil.

Bawaan bahasa sekarang **Inggris**; Bahasa Indonesia tersedia satu ketukan di Pengaturan → Tampilan,
begitu pula tema (bawaan terang). Pelengkapnya: splash beranimasi, onboarding empat halaman yang
bisa diputar ulang dari Tentang, halaman **Riwayat** berupa kalender (titik per hari, daftar ekspor,
statistik, bagikan ulang), lima palet warna plus Material You, pengaturan berbentuk kartu yang bisa
dibuka-tutup, serta sakelar animasi dan getaran.
