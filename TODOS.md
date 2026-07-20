# TODOS — Marginalia

Deferred work from the 2026-07-20 design + eng review sessions. Design doc of record:
`~/.gstack/projects/pdf-notes/serendeep-nobranch-design-20260720-002316.md`

## Liquid-glass UI polish
- **What:** Frosted/translucent surfaces (toolbar, outline sheet, dialogs) over the Cool Focus palette.
- **Why:** User wants a more modern, polished feel once core functionality lands.
- **Pros:** Distinctive, premium look. **Cons:** Backdrop blur is a classic jank source; needs on-device perf validation.
- **Context:** Compose has no real backdrop blur built in; use the Haze library. Keep the same design tokens, glass only on floating chrome, never on the writing sheet.
- **Depends on:** Core functionality complete; user sign-off on the look.

## Link overlays on rotated PDF pages
- **What:** Respect the page /Rotate flag when mapping link rectangles to screen positions.
- **Why:** mapRectToDevice currently passes rotation 0, so link taps misalign on rotated pages.
- **Pros:** Correctness on scanned/rotated decks. **Cons:** Needs a rotated fixture to test.
- **Context:** PdfDocumentSource.pageLinks; check whether the pdfium binding exposes page rotation.
- **Depends on:** A real rotated lecture PDF to verify against.

## OCR for scanned/image-only PDFs
- **What:** Make image-only PDFs text-searchable via on-device OCR.
- **Why:** They currently import fine but are excluded from search with a "no text layer" badge; a semester will contain some.
- **Pros:** Closes the last search gap. **Cons:** OCR quality/cost unknown; ML Kit text recognition is a separate model from Digital Ink.
- **Context:** Search lands in M3 via pdfium text extraction → FTS5. Scanned PDFs have no text layer to extract. Start at the M3 import indexing path.
- **Depends on:** M3 search shipping first.

## Cross-version anchor reconciliation
- **What:** Map notes anchored to slide-deck v1 onto a corrected v2 (page-matching across re-imports).
- **Why:** v1 policy is "new document, both versions listed, inactive strokes dimmed" — safe but clutters after repeated re-imports.
- **Pros:** Notes follow the professor's corrections. **Cons:** Page-matching heuristics are genuinely hard; wrong matches are worse than clutter.
- **Context:** Anchor rows carry `documentId` + `pdfPage`; reconciliation = producing a v1→v2 page map and rewriting anchors behind an undoable migration. Deferred until re-imports hurt in real usage.
- **Depends on:** Real usage data; several re-imported lectures.

## androidx.pdf migration watch
- **What:** Replace pdfium-android plumbing with Jetpack androidx.pdf when it reaches stable.
- **Why:** It's Google's long-term answer and already wires into androidx.ink (`EditablePdfViewerFragment`); alpha19 as of 2026-07.
- **Pros:** Deletes our tile/render layer eventually. **Cons:** Alpha churn; migration touches pdf/ wholesale.
- **Context:** Check the androidx.pdf release notes at each milestone boundary. Week-0 spike failure of pdfium promotes this to immediate evaluation.
- **Depends on:** androidx.pdf stable (or pdfium spike failing).

## Multi-module Gradle split
- **What:** Split the single module into core-ink/core-pdf/data/features.
- **Why:** Only when build times measurably hurt (eng review D5→3B).
- **Pros:** Build parallelism, enforced boundaries. **Cons:** Ceremony; premature for solo dev.
- **Context:** Package-by-feature layout (`library/`, `notebook/`, `ink/`, `pdf/`, `sync/`, `data/`) is already module-shaped — the split is mechanical when justified.
- **Depends on:** Measured build-time pain.

## Play Store name decision
- **What:** Keep "Marginalia" or switch to a variant ("Margina", "Annotalia") before any Play Store listing.
- **Why:** A different app ("Marginalia: Digitize Quotes") already holds the name in the Play note-taking neighborhood (checked 2026-07-20). Safe for sideload; discovery-risky on Play.
- **Pros of deciding at M5:** Zero cost until distribution matters. **Cons:** Package ID (`app id`) should be chosen defensively at repo init — renaming the applicationId later breaks installed-user upgrade paths.
- **Context:** Pick a neutral applicationId at init (e.g. `com.serendeep.marginalia` is fine either way — the *display* name is what may change).
- **Depends on:** M5 Play Store decision.
