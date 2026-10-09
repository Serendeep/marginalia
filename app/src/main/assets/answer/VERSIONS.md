# Bundled answer renderer libraries

All files are vendored unmodified (except katex.min.css, stripped of the woff/ttf fallbacks) so answers render fully offline.

| Library | Version | Licence | Source |
|---|---|---|---|
| markdown-it | 14.1.0 | MIT | https://cdn.jsdelivr.net/npm/markdown-it@14.1.0/dist/markdown-it.min.js |
| markdown-it-texmath | 1.0.0 | MIT | https://cdn.jsdelivr.net/npm/markdown-it-texmath@1.0.0/texmath.js |
| KaTeX (JS, CSS, woff2 fonts) | 0.19.0 | MIT | https://cdn.jsdelivr.net/npm/katex@0.19.0/dist/ |
| Mermaid | 11.12.0 | MIT | https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.min.js |
| highlight.js (common languages build) | 11.12.0 | BSD-3-Clause | https://cdn.jsdelivr.net/npm/@highlightjs/cdn-assets@11.12.0/highlight.min.js |

highlight.js ships the 36-language common build, which includes python, kotlin, java, javascript, typescript, c, cpp, bash, json and sql.
