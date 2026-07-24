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

## Now: notebooks and import flow

- [ ] Empty notebooks: blank pen-only notebooks without a backing PDF
- [ ] Register as a PDF handler: open PDFs from any app straight into Marginalia
- [ ] Ink on the page: highlighter and pen strokes directly on PDF pages,
      not just the margin

## Next

- [ ] Backup & restore: safe database export with integrity checks
- [ ] Pen customisation: nib styles, colors, stroke widths
- [ ] Full-text search across all lectures
- [ ] Fix link taps on rotated PDF pages
- [ ] Drag-to-reorder notebooks in the library
- [ ] Light theme
- [ ] Faster cold start (baseline profiles)

## Later

- [ ] OCR for scanned / image-only PDFs so they become searchable
- [ ] Carry notes across re-imported deck versions (v1 → v2 page matching)
- [ ] Migrate PDF rendering to androidx.pdf once it reaches stable
- [ ] Multi-module build split if build times warrant it
- [ ] App name review ahead of any Play Store listing
