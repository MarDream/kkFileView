# kkFileView Performance and Security Remediation Plan

## Goals

- Reduce first-preview latency for Office and PDF assets.
- Stabilize throughput under concurrent conversions.
- Remove invalid or risky dependency coordinates and make dependency audits repeatable.
- Harden remote fetch, encryption, and cache behaviors without breaking existing preview links.

## Current hot spots

1. PDF image-mode preview was repeatedly loading the same PDF document in parallel batches.
   This multiplies disk I/O, memory pressure, and PDFBox parse cost on large files.
2. HTML post-processing for converted spreadsheet previews rewrote entire files through an in-memory `StringBuilder`.
   Large HTML exports can create avoidable heap spikes.
3. HTTP connection-pool cleanup held the class monitor while sleeping.
   Under load this can delay new client creation and amplify request jitter.
4. Several dependency versions in `pom.xml` were not valid published releases.
   That makes builds fragile and blocks reliable upgrade or vulnerability scanning.
5. AES handling had moved to a safer mode, but without legacy decrypt compatibility it risked breaking existing encrypted links.

## Changes applied in this pass

1. Reworked PDF page rendering to a fixed worker-pool model.
   Each worker loads the document once and renders multiple pages from a shared page queue.
2. Enabled adaptive PDF worker sizing from `pdf.max.threads`, `pdf.max.threads.auto`, `pdf.max.threads.baseline`, and `pdf.max.threads.max`.
3. Streamed converted HTML rewrites through a temp file and prevented duplicate Excel helper injection.
4. Fixed the HTTP idle-monitor lock contention path.
5. Switched AES encryption to CBC for new payloads while preserving legacy ECB decrypt support.
6. Normalized security defaults so AES and delete-password protection are disabled by default instead of relying on hardcoded secrets.
7. Corrected dependency coordinates to published releases and moved Bouncy Castle to the JDK 25 compatible `bcprov-jdk18on`.
8. Added an AES compatibility regression test.
9. Moved `aspose-cad` behind an explicit Maven profile so default builds no longer depend on a vendor repository being reachable.

## Dependency governance

### Updated now

- `org.redisson:redisson` -> `4.4.0`
- `org.rocksdb:rocksdbjni` -> `10.10.1`
- `org.apache.pdfbox:pdfbox` / `pdfbox-tools` -> `3.0.7`
- `org.bouncycastle:bcprov-jdk18on` -> `1.84`
- `com.thoughtworks.xstream:xstream` -> `1.4.21`

### Corrected to real published coordinates

- `org.apache.commons:commons-imaging` stays on `1.0.0-alpha6`
  because `1.4.0` is not a published Maven Central release.

### Still recommended for a staged follow-up

- Apache POI / XDocReport compatibility matrix validation before upgrading both together.
- Remove or isolate `system` scoped JAI jars if image-path coverage proves they are no longer required.
- Add a dedicated security-audit Maven profile for OWASP Dependency-Check in CI once build stability is confirmed.

### Build note for CAD preview

- Default builds now compile and package without `com.aspose:aspose-cad`.
- To bundle Aspose CAD support, build with `-Pwith-aspose-cad`.
- If that profile is not enabled, CAD preview must use the external `cadviewer` path or it will fail fast with a clear configuration error instead of hanging during conversion.

## Performance evaluation plan

### KPIs

- P50 / P95 time to first preview page for:
  - Office -> PDF mode
  - Office -> image mode
  - Native PDF -> image mode
  - Archive-contained Office preview
- Throughput under concurrent requests:
  - 5, 10, 20 concurrent preview starts
- Resource cost:
  - CPU saturation
  - heap growth
  - full GC count
  - LibreOffice worker utilization

### Test layers

1. Unit and focused regression tests for config, crypto, and filter logic.
2. E2E smoke plus perf smoke under `tests/e2e/specs/perf-smoke.spec.ts`.
3. Load test against representative fixtures:
   - small PDF
   - 100+ page PDF
   - medium DOCX
   - PPTX with images
   - XLSX web preview
4. Production-like benchmarking with warm cache and cold cache runs separated.

### Benchmark method

1. Cold start: clear converted artifacts and caches.
2. Warm path: repeat the same preview 3 to 5 times.
3. Mixed workload: 70% cached preview, 30% fresh conversion.
4. Record JVM and host metrics alongside endpoint timings.

## Security review checklist

1. Keep `kk.ignore.ssl=false` and `kk.enable.redirect=false` in production.
2. Require explicit `trust.host` and keep internal-network patterns in `not.trust.host`.
3. Treat AES as opt-in and supply `KK_AES_KEY` only when an integration truly needs encrypted preview URLs.
4. Treat file deletion password as opt-in and prefer captcha for interactive admin flows.
5. Review whether `cache.type=default` is ever used in production; the current RocksDB map-serialization design is not suitable for high write volume.
6. Add CI dependency scanning after the dependency set stabilizes.

## Recommended next phase

1. Introduce a bounded async executor for Office conversion submission and wire `office.queue.max.size` into admission control.
2. Add metrics around conversion queue wait time, LibreOffice task time, and PDF render page rate.
3. Split static-asset and generated-file cache-control policies if CDN/browser caching becomes a major latency factor.
4. Revisit remote fetch safeguards for hostname-to-private-IP resolution checks in tightly controlled deployments.
