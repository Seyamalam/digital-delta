# Mission headquarters captures

Source revision: `6b6f174`. Captured September 5, 2026 using the production Next.js
build and Argent-controlled Chromium 152 on macOS, device scale factor 1.

The browser connected to local Hono/D1 through SSE. All mission and travel-time
inputs use the visibly simulated `mission-qa-v1` fixture. Geography is bundled
OpenStreetMap data. These are working UI captures, not design mockups, but they do
not prove real vehicle locations, road safety or physical-phone relay acceptance.

| File | Viewport | Language | State |
| --- | --- | --- | --- |
| missions-bn-1366x768.png | 1366×768 | Bangla | Register first; medical breach and supplies within SLA |
| missions-en-1366x768.png | 1366×768 | English | Same register and simulation labels |
| missions-bn-1920x1080.png | 1920×1080 | Bangla | Register and selected mission detail side by side |
| missions-en-1920x1080.png | 1920×1080 | English | Same selected medical mission and evaluation |
| map-selected-en-1920x1080.png | 1920×1080 | English | Supplies selection retained after navigation; scoped route and SLA labels |

The smaller viewport intentionally scrolls to the detail below the register. The
map capture is the top viewport of a scrollable map page, not a full-page export.
Human accessibility review, actual projector readability at the venue, and a fresh
complete pitch screenshot set remain separate acceptance checks.

The exact capture timestamps are in `artifacts/screenshots/manifest.csv`.
