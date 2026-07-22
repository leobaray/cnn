# CNN — Converter Dataset Builder & Identifier

A two-part system for building a labeled image dataset of converter units in the field and identifying them from a single photo with a convolutional neural network. An Android client (Kotlin / Jetpack Compose) captures and uploads standardized photos; a FastAPI backend organizes the dataset on disk and serves PyTorch inference.

## Context

Training an image classifier for physical parts is bottlenecked by the dataset: you need hundreds of consistent, well-labeled photos per class, captured under controlled conditions, without a laptop in hand. This project turns a phone into that capture station. Each converter is a class; each class is filled across 16 standardized capture setups (275 photos each, 4,400 per converter); photos stream to the server through resilient background uploads. Once a model is trained, the same app identifies an unknown converter and shows the top-5 predictions.

The repository holds two deployable components: the Android client and the API backend. The model-training pipeline lives in a **separate `ml/` repository** that the server imports at runtime — it is not included here.

## Components

- **`apk/`** — Android application, Kotlin + Jetpack Compose (Material 3).
- **`server/`** — FastAPI REST backend: dataset management, thumbnailing, and inference.

## Features

### Android client
- **Biometric login** (`BIOMETRIC_STRONG`) with credentials stored in `EncryptedSharedPreferences` (AES-256-GCM); manual HTTP Basic fallback.
- **10-minute inactivity timeout** that logs the user out and returns to the login screen.
- **CameraX capture** with a drag-to-lock **burst mode** (380 ms interval) and automatic square crop to 512×512.
- **Structured capture protocol**: 16 predefined filters (`f01`–`f16`) combining cover/cloth, bench/table and new/old, 275 photos per filter.
- **Resilient uploads**: bounded concurrency (3 in flight), exponential-backoff retry (3s → 60s, 10 attempts), surviving screen navigation via a `SupervisorJob`.
- **Two-phase review** (grid cleanup + swipe keep/discard) with pending photos persisted between sessions.
- **On-device thumbnail cache** plus lazy server-side thumbnails, loaded through Coil with an auth interceptor.
- **AI identify**: capture → `/infer` with test-time augmentation → top-5 classes with confidence; the last 20 identifications are kept locally.
- **In-app forced update**: version check, APK download with a progress overlay, install through `FileProvider`.
- **Persistent photo-count cache** that flips a converter to a "complete" state at 4,400 photos and reacts immediately.

### FastAPI backend
- **HTTP Basic auth** backed by a local, non-versioned `users.json`.
- **Dataset CRUD**: create/rename/delete converter folders; list/upload/delete/download photos.
- **Safe uploads**: streamed to a temp file, image type verified by magic bytes, sanitized filenames, per-filter prefixes.
- **On-the-fly JPEG thumbnails** (Pillow, configurable size).
- **PyTorch inference**: lazy model load from the sibling `ml/` repo, softmax top-5, optional 7-way test-time augmentation (flips + rotations), CUDA when available.
- **App-distribution endpoints** (`/app/version`, `/app/download`) that power the client's self-update.

## Tech stack

**Android** — Kotlin 2.2.10, Android Gradle Plugin 9.1.1, Gradle 9.3.1, Jetpack Compose (BOM 2024.09.00) + Material 3, CameraX 1.3.4, OkHttp 4.12.0, Coil 2.6.0, AndroidX Security Crypto 1.1.0-alpha06. `minSdk 33`, `targetSdk`/`compileSdk 36`, Java 11.

**Backend** — Python, FastAPI ≥ 0.110, Uvicorn ≥ 0.29, python-multipart ≥ 0.0.9, Pillow ≥ 10, NumPy ≥ 1.26, PyTorch ≥ 2.10, TorchVision ≥ 0.25, scikit-learn ≥ 1.4, Matplotlib ≥ 3.8.

## Architecture

```
        ┌───────────────────────────────┐
        │  Android app (apk/)           │
        │  Compose · CameraX · OkHttp   │
        │  biometric + encrypted creds  │
        └───────────────┬───────────────┘
                        │  HTTPS · HTTP Basic
                        │  multipart upload / infer
                        ▼
        ┌───────────────────────────────┐
        │  FastAPI server (server/)     │
        │  dataset CRUD · thumbnails    │
        │  PyTorch inference (/infer)   │
        └───────┬───────────────┬───────┘
                │               │
       dataset on disk     trained model
       ml/datasets/*       ml/output/models/best_model.pt
                                ▲
                                │ produced by
                       ┌────────┴──────────┐
                       │  ml/ repository   │  (external — not in this repo)
                       │  training + train.build_model
                       └───────────────────┘
```

The server resolves the `ml/` directory as a sibling of this repository, reads/writes datasets under `ml/datasets/`, and loads the checkpoint from `ml/output/models/`.

## Getting started

### Backend

```bash
cd server
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt

# Create the auth file (not versioned): { "username": "password", ... }
printf '{"admin":"changeme"}' > users.json

python server.py            # Uvicorn on 0.0.0.0:52500
```

Dataset endpoints run standalone. The inference endpoints (`/infer`, `/infer/status`) additionally require the external `ml/` repository checked out **next to** this one and a trained checkpoint at `ml/output/models/best_model.pt` (plus `meta.json`).

### Android client

The client targets `https://server.lbwma.com`, hardcoded in `ApiClient.kt` — change `baseUrl` to your own server before building.

```bash
cd apk
./gradlew assembleDebug      # build a debug APK
./gradlew installDebug       # install on a connected device (Android 13+)
```

Release builds are signed from a `keystore.properties` + keystore that are intentionally not committed; debug builds need neither.

### Tests

```bash
cd apk
./gradlew test                 # JVM unit tests
./gradlew connectedAndroidTest # instrumented tests (device/emulator)
```

Only the default AndroidX example tests are present; there is no meaningful automated coverage yet.

## Project structure

```
cnn/
├── apk/                                   # Android client (Kotlin / Jetpack Compose)
│   ├── app/src/main/java/com/lbwma/cnn/
│   │   ├── MainActivity.kt                # depth-based navigation, session timeout, update overlay
│   │   ├── BiometricHelper.kt             # biometric auth + encrypted credential store
│   │   ├── model/                         # Filtro, PhotoCountCache, RefinementStore, IdentificationHistory
│   │   ├── network/                       # ApiClient, UploadManager, AppUpdater, ThumbnailCache
│   │   ├── screen/                        # Login, Identify, Converters, FilterGrid, Camera, Review, Photos, Settings
│   │   └── ui/                            # design system + Material 3 theme
│   ├── app/build.gradle.kts               # module config (SDK levels, signing, deps)
│   └── gradle/libs.versions.toml          # version catalog
├── server/
│   ├── server.py                          # FastAPI app: dataset CRUD, thumbnails, /infer
│   └── requirements.txt
├── SECURITY.md
└── README.md
```

## Status & limitations

- Personal, single-tenant project wired to the author's own infrastructure and domain.
- Inference depends on an external `ml/` training repo (not included) and a trained checkpoint; without them, only dataset collection works.
- Auth is HTTP Basic over TLS with a flat `users.json`; there is no user-management UI.
- The client's server URL is hardcoded and must be edited to self-host.
- No CI; the test suite is scaffolding only.

## License

Released under the MIT License.
