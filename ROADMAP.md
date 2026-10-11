# Marginalia Roadmap

Marginalia is a pen-first Android app for taking margin notes on lecture PDFs.
This roadmap tracks planned work; items move up as they get scheduled. PRs
welcome, see [CONTRIBUTING.md](CONTRIBUTING.md).

## Done

- [x] Library redesign ("Quiet Archive"): list rows with real PDF thumbnails,
      monospaced metadata, hairline rules, and a single cyan accent
- [x] Course customization: user-picked color and emoji per course
- [x] Rename, move, and delete lectures, including cleanup of imported files
      and related notes
- [x] Quiet empty-library state and a single add menu for imports and courses
- [x] Editor chrome restyle, including the active-tool ring and page indicator
- [x] Fix "1 pages" pluralization in the library
- [x] Refresh README screenshots with seeded course, lecture, and margin ink
- [x] Empty notebooks: create a titled, blank pen-only notebook without a PDF

## Done in 1.0: the study machine

- [x] Register as a PDF handler: open PDFs from any app straight into Marginalia
- [x] Ink on the page: highlighter and pen strokes directly on PDF pages
- [x] Today dashboard, automatic study-time tracking, daily goal and streaks
- [x] Reading status (to read / reading / done) and resume at the last page
- [x] Full-text search across PDF text, highlights and handwriting
- [x] Smart highlighter that captures the text under it; Markdown export
- [x] Flashcards from lasso, highlights and typing; SM-2 spaced review; daily reminder
- [x] Tags, arXiv/DOI detection, BibTeX citations, Stats screen
- [x] Pencil tools: hold-to-shape, scratch-out, ink selection, laser, pen widths and colours, eraser sizes
- [x] On-device handwriting recognition (convert to text, searchable notes)
- [x] Optional ChatGPT features via "Sign in with ChatGPT", or any OpenAI-compatible endpoint
- [x] Pen strokes on PDF pages no longer scroll the page
- [x] Faster cold start (baseline profile) and R8-shrunk release builds

## Done in 1.2 to 1.4

- [x] In-app updates from GitHub Releases, with delta patches and Stable/Nightly channels
- [x] Backup & restore: verified zip export, nightly automatic backups, safety snapshots
- [x] Light theme
- [x] Handwritten flashcard answers
- [x] Drag-to-reorder notebooks in the library
- [x] Saved chat history and an opt-in morning digest

## Next

- [ ] Fix link taps on rotated PDF pages
- [ ] Crash log you can attach to a bug report (release builds are shrunk, so stack traces need the mapping file)
- [ ] Export annotated pages as a PDF with the ink drawn in
- [ ] Translations: every string is still a Kotlin literal

## Later

- [ ] OCR for scanned / image-only PDFs so they become searchable
- [ ] Carry notes across re-imported deck versions (v1 → v2 page matching)
- [ ] Migrate PDF rendering to androidx.pdf once it reaches stable
- [ ] Multi-module build split if build times warrant it
- [ ] App name review ahead of any Play Store listing
