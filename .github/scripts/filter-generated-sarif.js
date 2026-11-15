#!/usr/bin/env node
/**
 * Filter AGP-generated-code findings out of CodeQL SARIF files. IN PLACE.
 *
 * WHY THIS EXISTS (do not remove without owner approval — fleet security
 * standard, owner-directed 2026-11-15):
 *
 * The Android Gradle Plugin generates data-binding classes under
 * `app/build/generated/` during every Gradle build. Those files are NOT
 * part of this repository (`git ls-files app/build` returns nothing) and
 * are regenerated on every build, so findings located there cannot be
 * fixed at source. CodeQL's advanced workflow builds the project to trace
 * compilation, which extracts those generated files and reports findings
 * in them. All five documented exclusion mechanisms were tried and none
 * suppresses findings from a traced java-kotlin database:
 *
 *   1. codescanning-config `paths_exclude`  -> CLI 2.27.4: "Invalid property
 *      specified in the configuration file. Ignoring it."
 *   2. codescanning-config `paths_ignore`   -> same CLI rejection.
 *   3. codescanning-config `paths-ignore` (hyphenated, documented form;
 *      accepted without warning, glob and plain-path variants) -> does not
 *      filter a compiled/traced database.
 *   4. `build-mode: none` (buildless)       -> fatal for this pure-Kotlin
 *      project (111 .kt / 0 .java): "CodeQL could not process any code
 *      written in Java/Kotlin."
 *   5. `CODEQL_EXTRACTOR_JAVA_EXCLUDE` env (workflow-level glob and exact
 *      build-step pattern) -> accepted, no effect on the findings.
 *
 * Therefore, per the owner's explicit direction, results whose PRIMARY
 * location is under `app/build/` are removed from the SARIF between
 * analysis and upload — same spirit as the owner's approved practice of
 * deleting scan data for code that does not exist in the repository.
 *
 * CANARY / GUARDRAILS (binding):
 *   - Filter NARROWLY: only `app/build/` primary locations. NEVER widen
 *     this prefix. First-party source findings MUST keep flowing: the
 *     pipeline is proven to report real findings (the java/relative-path-
 *     command findings in RootChecker.kt were reported and auto-closed by
 *     a code fix). If first-party findings ever stop appearing, this
 *     filter is broken — fix it, do not touch the prefix.
 *   - No API dismissals are used; the next same-key SARIF upload without
 *     these results closes the alerts through normal code-scanning
 *     reconciliation.
 *
 * Usage: node filter-generated-sarif.js <sarif-directory>
 */

const fs = require("fs");
const path = require("path");

const EXCLUDED_PREFIX = "app/build/";

const dir = process.argv[2];
if (!dir) {
  console.error("usage: node filter-generated-sarif.js <sarif-directory>");
  process.exit(2);
}

function primaryLocationPath(result) {
  const uri = result?.locations?.[0]?.physicalLocation?.artifactLocation?.uri;
  if (typeof uri !== "string") return null;
  try {
    return decodeURIComponent(uri).replace(/\\/g, "/");
  } catch {
    return uri.replace(/\\/g, "/");
  }
}

let totalRemoved = 0;
let totalKept = 0;

for (const file of fs.readdirSync(dir)) {
  if (!file.endsWith(".sarif")) continue;
  const full = path.join(dir, file);
  const sarif = JSON.parse(fs.readFileSync(full, "utf8"));
  for (const run of sarif.runs ?? []) {
    const results = run.results ?? [];
    const kept = results.filter((r) => {
      const p = primaryLocationPath(r);
      const excluded = p !== null && p.startsWith(EXCLUDED_PREFIX);
      if (excluded) totalRemoved++;
      else totalKept++;
      return !excluded;
    });
    run.results = kept;
  }
  fs.writeFileSync(full, JSON.stringify(sarif));
  console.log(`[filter-generated-sarif] ${file}: processed`);
}

console.log(
  `[filter-generated-sarif] removed ${totalRemoved} generated-code findings ` +
    `(primary location under "${EXCLUDED_PREFIX}"), kept ${totalKept} first-party findings.`
);
