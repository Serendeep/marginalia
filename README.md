# Marginalia

Handwritten lecture notes next to the slides, for Android tablets.

Marginalia keeps your course PDFs in one place and lets you write beside them
with a stylus instead of scribbling on top of the page. Notes stay anchored to
the page you wrote them against. The stylus draws; your finger scrolls and
zooms. There's no drawing/scrolling mode to toggle.

It's an Android-only, offline-first personal project built with Kotlin and
Jetpack Compose.

## Status

Early, and built in pieces:

- [x] Storage: courses, lectures, documents, and strokes in Room
- [x] PDF pane: open a PDF and scroll through it
- [ ] Ink pane: write, erase, and undo with the stylus
- [ ] Linked two-pane layout with scroll sync
- [ ] Course library and PDF import

Right now the app opens to a single screen where you pick a PDF and read it.

## Building

You need the Android SDK and JDK 17 or newer.

```
./gradlew installDebug
```

Launch it from the tablet, or start it over adb:

```
adb shell am start -n com.serendeep.marginalia/.MainActivity
```

Run the tests on a connected device or emulator:

```
./gradlew connectedDebugAndroidTest
```

## Built with

- Kotlin, Jetpack Compose, Hilt
- Room for storage
- [pdfium](https://github.com/legere-org/pdfiumandroid) for PDF rendering
- [androidx.ink](https://developer.android.com/jetpack/androidx/releases/ink) for handwriting

## Notes

Known gaps and deferred work are in [TODOS.md](TODOS.md).
