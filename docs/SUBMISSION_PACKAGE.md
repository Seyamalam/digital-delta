# Submission package

Prepared on 4 September 2026 for the Bangladesh Innovation Fair.

## Final deliverables

| Deliverable | Repository path | Purpose |
|---|---|---|
| Editable pitch deck | `output/presentation/digital-delta-innovation-fair.pptx` | Eight-slide application and live pitch deck |
| Editable report | `output/docx/digital-delta-report.docx` | Full technical, market, pilot, safety, and evidence report |
| PDF report | `output/pdf/digital-delta-report.pdf` | Optional supporting document and shareable report |
| Pitch manuscript | `docs/PITCH_DECK.md` | Slide narrative, sources, and speaking notes |
| Demo script | `DEMO.md` | Ten-minute demonstration and ninety-second booth loop |
| Evidence checklist | `TODO.md` | Exact completed and open gates |
| Screenshot plan | `SCREENSHOTS.md` | Required visual proof and capture status |

## Integrity manifest

Run `shasum -a 256` against the three binary deliverables and compare the results with the manifest below before submitting. The manifest is updated by the final packaging pass.

```text
67c047be4d874835f4cffd9b0d54d5b4054890d43e0ef032a6d6375a94e1b69c  output/presentation/digital-delta-innovation-fair.pptx
f3ed3e57569514133285d7e93e23fc08618d75602b4139b3bf1cb4d810fa8440  output/docx/digital-delta-report.docx
bc24c916a85c027593c6450b89132f96bdeb24c59a93961467ce56828f1963fc  output/pdf/digital-delta-report.pdf
```

## Verification summary

- `scripts/verify-local.sh --connected` passes from the final source tree.
- Sixty unchanged connected Android tests pass on Android 15 and Android 16 emulators.
- The Go race, vet, build, observer, durable relay, provisioning, and load-test paths pass.
- Seventeen Next.js dashboard tests, TypeScript validation, and the production build pass.
- Three Cloudflare archive tests and the Wrangler dry run pass.
- The synthetic route-risk model reproduces its recorded metrics and ONNX parity result.
- The pitch deck passes Office package-integrity and slide-geometry inspection, and all eight LibreOffice renders were visually inspected.
- The report is 16 pages; all pages were rendered and visually inspected. The accessibility audit has no high-severity findings. Its six medium findings are intentional layout tables, while semantic data tables contain header rows.

The dated verification record is `artifacts/reports/verification/2026-09-04-final-local-gate.md`. The public-claim audit is `artifacts/reports/claim-audit/2026-09-04.md`.

## Paper Amigo copy

- Project ID: `6879b659-e9f0-4568-853b-d8e8fba38a14`
- Project title: `Digital Delta: Offline Disaster Logistics for Bangladesh`
- Uploaded file: `digital-delta-report.pdf`
- File URL: https://yzy16yxaqa.ufs.sh/f/QMVxAv8DZmlKLwarMvnyoZ16pANvdRreizL4jYHhq0f7SbXn

The uploaded PDF hash is `bc24c916a85c027593c6450b89132f96bdeb24c59a93961467ce56828f1963fc`, matching the local integrity manifest.

## Honest release boundary

The software package is complete, but the project is not represented as field-verified. Sixteen checklist items remain open because they require organizer information, named physical phones, human reviewers, or repeated live rehearsals. They include the real-camera airplane-mode pass, three-phone relay and recovery, dashboard-disconnection proof with phones, target-device RAM and latency measurements, the human bilingual/accessibility matrix, complete physical screenshots, twenty rehearsals, three unchanged final passes, and the optional backup video.

These items cannot be replaced by emulator output or fabricated evidence. Use `docs/PHYSICAL_DEVICE_TEST.md` and `docs/TESTING.md` to collect them on the final booth hardware. A software, data, model, or script change after the three final passes resets that count.

## Submission order

1. Confirm participation, the final deadline, pitch duration, and booth equipment with the organizer.
2. Run the physical-device sheet on the exact phones and laptops that will go to the fair.
3. Complete the human accessibility matrix and capture the remaining evidence.
4. Run twenty rehearsals, then record three consecutive unchanged passing demos.
5. Re-run the local verification gate and regenerate checksums only if the source or artifacts changed.
6. Upload the PPTX, attach the PDF as the supporting document, and choose a bilingual representative screenshot.
