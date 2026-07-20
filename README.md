# Marginalia

Linked side-by-side lecture notes for Android tablets — PDFs organized by course,
low-latency stylus handwriting in a pane *beside* the slides. Stylus writes,
finger scrolls, no modes. Kotlin / Jetpack Compose, Android-only, offline-first.

Design doc of record: `~/.gstack/projects/pdf-notes/serendeep-nobranch-design-20260720-002316.md`
Deferred work: [`TODOS.md`](TODOS.md)

## Status: Week-0 pdfium spike

Before committing to pdfium as M1's PDF engine, this repo is currently a
validation spike. It boots to `PdfiumSpikeScreen`, where you pick a real lecture
PDF and see render times, page sizes, and extracted text.

### Stack (locked in eng review)

- Kotlin 2.0 · Jetpack Compose · single Gradle module · Hilt DI (wired, unused so far)
- PDF engine: `io.legere:pdfiumandroid` (maintained Apache-2.0 pdfium binding)
- minSdk 29 · compileSdk 35

## Run the spike

### On your tablet (the real test)

```bash
export ANDROID_HOME=$HOME/Android
./gradlew installDebug          # tablet connected via USB, debugging on
adb shell am start -n com.serendeep.marginalia/.MainActivity
```

Then tap **Pick a lecture PDF** and load, in turn:

1. a normal text PDF (slides)   → expect `Text layer: YES`, render well under a frame
2. a **scanned** PDF            → expect `Text layer: NO` (correct — no OCR yet)
3. a **rotated** PDF            → expect correct page dimensions, upright render
4. a **300+ page** PDF          → expect fast open, sampled pages render fine, no OOM

### Pass bars (from the design doc)

- opens without crashing across scanned / rotated / huge files
- per-page render comfortably under a frame budget (target < ~50ms at ~1080px width)
- text PDFs report `charCount > 0` with readable sample text
- scanned PDFs report `charCount == 0` (→ "no text layer", as designed)

If pdfium fails any of these on your hardware, the fallback is to evaluate
`androidx.pdf` (still alpha) — see TODOS.md.

### Automated half (emulator or device)

```bash
./gradlew connectedDebugAndroidTest
```

`PdfiumSpikeTest` generates a text-bearing PDF at runtime and asserts pdfium
renders it and extracts the text.
