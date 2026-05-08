# White-Box Test Report — Library-DB-Express

**Course:** SE 2226 — Software Quality Assurance and Testing
**Sub-project:** Library-DB-Express (Node.js / Express / Sequelize / SQLite)
**Standard:** ISO/IEC/IEEE 29119-3:2021 §7.4 — *Test completion report*
**Reference template:** [SE 2226 Test Completion Report Template](../../docs/SE%202226%20Test%20Completion%20Report%20Template.pdf)
**Test plan:** [test_plan.pdf](../../docs/test_plan.pdf) (19 April 2026)
**Issue list:** [whitebox_test_issues.md](../../docs/whitebox_test_issues.md)
**Report date:** 2026-05-08
**Reporting period:** 2026-04-29 — 2026-05-08

| Member | Student no. | Primary roles |
| --- | --- | --- |
| Atakan Sezginer | 24070006126 | Unit-test developer |
| Batuhan Yerebasmaz | 23070006029 | White-box tester |
| Ege Çınar | 24070006020 | White-box tester |
| İsmet Sayğın Koç | 23070006038 | Unit-test developer |
| Osman Şahin Güler | 24070006009 | Test lead |

---

## 1. Summary of testing performed

This report documents the white-box testing of [whitebox/Library-DB-Express/](.), the Node.js + Express + Sequelize + SQLite library application that serves as the structural-testing target for SE 2226. It is the predecessor of [TEST_COMPLETION_REPORT.md](TEST_COMPLETION_REPORT.md), which is the engagement closeout written against the same template.

### 1.1 Test items
- [app.js](app.js) — Express bootstrap, middleware wiring, sequelize sync IIFE.
- [routes/index.js](routes/index.js) — All seven CRUD/search routes.
- [models/book.js](models/book.js), [models/index.js](models/index.js) — Sequelize `Book` model and dynamic loader.
- [errorHandlers.js](errorHandlers.js) — `fourOhFour` and `globalError` middleware.

### 1.2 Test type and level
Unit testing using **structural (white-box) techniques** at the route, model, and middleware level. The Express app is exercised through `supertest` against a fresh in-memory SQLite fixture (per [whitebox_test_issues.md](../../docs/whitebox_test_issues.md) §Notes); no real `library.db` data is touched.

### 1.3 Techniques applied (test_plan.pdf §3.5)
Each technique is implemented as a **dedicated test file** so traceability between the plan's technique list and the executable suite is one-to-one.

| Technique | Test file | Tests |
| --- | --- | ---: |
| Statement Coverage (SC) | [\_\_tests\_\_/statement_coverage.test.js](__tests__/statement_coverage.test.js) | 19 |
| Decision (Branch) Coverage (BC) | [\_\_tests\_\_/decision_coverage.test.js](__tests__/decision_coverage.test.js) | 18 |
| Basis Path Testing (McCabe V(G)) | [\_\_tests\_\_/basis_path.test.js](__tests__/basis_path.test.js) | 17 |
| Boundary Value Analysis (BVA) | [\_\_tests\_\_/boundary_value.test.js](__tests__/boundary_value.test.js) | 24 |
| Data-Flow Testing (def-use pairs) | [\_\_tests\_\_/data_flow.test.js](__tests__/data_flow.test.js) | 16 |
| Mutation testing as quality oracle | [stryker.conf.js](stryker.conf.js) + Stryker 7.3.0 | 100 mutants tested |
| Behavioural anchors (issue list) | `routes.test.js`, `models.test.js`, `errorHandlers.test.js`, `server.test.js`, `mutation.test.js` | 58 |

### 1.4 Constraints honored
- Synthetic test data only (per the test plan); no real personal information in fixtures.
- All tests run against an isolated SQLite fixture rebuilt with `sequelize.sync({ force: true })` in `beforeAll`.
- No code under test was changed to satisfy a test, **except** the dead-data fix at [views/layout.pug](views/layout.pug) — see §3.3 defect WB-D1.

---

## 2. Deviations from planned testing

| # | Plan reference | Plan called for | Actual | Reason |
| - | --- | --- | --- | --- |
| WB-D1 | test_plan.pdf §1.2, §3.10 | White-box tests in Java with JUnit 6 inside IntelliJ IDEA | JavaScript with Jest 30.3.0 inside the Node.js project | The system under test is a Node.js/Express app; tests in the project's own language was the only practical option. |
| WB-D2 | test_plan.pdf §3.3 | IntelliJ built-in coverage runner | Jest's built-in coverage (`--coverage`) and Stryker 7.3.0 for mutation | Direct consequence of WB-D1; both produce IEEE-29119-grade artifacts (lcov + interactive HTML). |
| WB-D3 | test_plan.pdf §3.6 | Targets stated in terms of SC ≥ 80 %, BC ≥ 75 % | Achieved SC = 98.26 %, BC = 82.60 %, plus mutation score 95.00 % | Targets exceeded; mutation score added as a stronger structural-quality oracle. |
| WB-D4 | [whitebox_test_issues.md](../../docs/whitebox_test_issues.md) issues 1–10 | One ownership pair per issue | Same ownership preserved, with five additional cross-cutting technique files added in this session | Course-driven request to add dedicated white-box-technique files; ownership unchanged. |

The deviations do not introduce new residual risks; they substitute equivalent tooling for the planned tooling and tighten (not loosen) the exit criteria.

---

## 3. Test completion evaluation

### 3.1 Exit criteria from the test plan

| # | Criterion | Target | Actual | Status |
| - | --- | --- | --- | :---: |
| WB-E1 | All planned issue-list test cases executed | 10/10 issues covered | 10/10 (each issue mapped to ≥ 1 test in §5) | ✅ Met |
| WB-E2 | Statement coverage ≥ 80 % | ≥ 80 % | **98.26 %** | ✅ Met |
| WB-E3 | Branch coverage ≥ 75 % | ≥ 75 % | **82.60 %** | ✅ Met |
| WB-E4 | All Jest tests pass | 100 % pass | 152 / 152 pass | ✅ Met |
| WB-E5 | Mutation score (added in this session) | ≥ 80 % | **95.00 %** | ✅ Met |
| WB-E6 | Zero surviving mutants in non-defensive code | 0 | 0 (5 NoCoverage in unreachable defensive paths) | ✅ Met |

### 3.2 Residual risks
1. **Defensive code paths.** [app.js:24](app.js#L24) (sequelize.authenticate failure log) and [models/index.js:14](models/index.js#L14) (env-driven config branch) are not exercised because they require fault injection into Sequelize. Five mutants in these paths are reported as `NoCoverage`. **Treatment:** accept; both paths are dead code in test environments and are exercised only when production credentials are absent.
2. **Static mutants on the Book schema.** 64 of Stryker's 164 mutants target `models/book.js` schema construction. Sequelize evaluates the schema once at module-load time and Jest caches the result, so Stryker's runtime mutation flag cannot toggle them per-test. **Treatment:** documented in [stryker.conf.js](stryker.conf.js) by setting `ignoreStatic: true` with `coverageAnalysis: 'perTest'`, which Stryker recognises as the correct handling for this class of mutants.

### 3.3 Defect log

| ID | Source location | Description | Resolution |
| --- | --- | --- | --- |
| WB-D1 | [routes/index.js](routes/index.js) calls `res.render('new-book', { title: "New Book" })` and the equivalent on the update view | The `title` local was passed but never consumed by any Pug template (see pre-fix `views/new-book.pug`, `views/update-book.pug`, `views/layout.pug`). Discovered when 2 mutants on the `"New Book"` literal survived as **equivalent mutants** under mutation testing. | [views/layout.pug](views/layout.pug) updated to render `title= title` in `<head>`; matching assertions added in [\_\_tests\_\_/routes.test.js](__tests__/routes.test.js). Both mutants now killed; final score lifted from 93.00 % → 95.00 %. |
| WB-D2 | [stryker.conf.js](stryker.conf.js) | `mutator: 'javascript'` and `htmlReporter.baseDir` were deprecated; `@stryker-mutator/html-reporter` was a stub package; `package.json`'s `test` script used Windows-only `set NODE_ENV=test&&`. | Config modernised; stub package removed; `npm test` and `npm run mutation` are now cross-platform. |
| WB-D3 | [\_\_tests\_\_/](__tests__/) | Five test files were named `*_test.js` and were silently skipped by Stryker's `testMatch: '**/*.test.js'` pattern. | Files renamed to `*.test.js` via `git mv`; Stryker now sees all 10 suites. |

WB-D1 is the only finding affecting the application under test. WB-D2 and WB-D3 are tooling/process defects.

### 3.4 Verdict
All six exit criteria are satisfied. The white-box engagement is **complete**.

---

## 4. Factors that blocked progress

| # | Blocker | Solution |
| - | --- | --- |
| WB-B1 | Native `sqlite3` binding shipped in `node_modules` was a Windows build (`napi-v3-win32-x64`) and could not load on the Linux test host. | `npm rebuild sqlite3` produced the Linux binding; all 10 Jest suites then loaded. |
| WB-B2 | `node_modules/.bin` symlinks lacked the executable bit on the Linux clone. | Recursive `chmod +x` over `node_modules/.bin`; long-term fix is a clean reinstall in CI. |
| WB-B3 | Node.js 18 + Stryker 8 were incompatible. | Stryker pinned to 7.3.0 (LTS line) in [package.json](package.json). |
| WB-B4 | First-pass `models.test.js` asserted `"Please Provide a Value For Title"` even when `title` was *omitted*. Sequelize emits `"Book.title cannot be null"` for the omitted case (`allowNull: false`) and the custom message only when the field is **present but empty** (`notEmpty`). | Tests rewritten to send empty strings; null cases asserted separately with the correct expected message. |
| WB-B5 | First-pass boundary test assumed `book.id === 1` after a fresh sync; SQLite autoincrement does not always start at 1 across `truncate: true` runs. | Loosened to `>= 1` and lookup by the returned `book.id`. |
| WB-B6 | After enabling Stryker, 14 mutants on `models/book.js` survived with `testsCompleted: 0`. Investigation revealed they were **static mutants**: schema is built at module load before any test runs, and Jest's module cache makes the runtime mutation flag a no-op for that code. | Switched [stryker.conf.js](stryker.conf.js) to `coverageAnalysis: 'perTest'` + `ignoreStatic: true`. Mutation count dropped from 164 to 100 *real* mutants and the score became meaningful. |
| WB-B7 | After the static-mutant fix, 2 mutants on `routes/index.js` `"New Book"` string still survived. They turned out to be **equivalent mutants** because no template consumed the `title` local. | Fixed at the source (WB-D1): the `<title>` element in [views/layout.pug](views/layout.pug) now renders the local. |

None of the blockers required behavioural changes to the application beyond WB-D1.

---

## 5. Test measures

### 5.1 Test execution

| Metric | Value |
| --- | --- |
| Test files (Jest suites) | 10 |
| Test cases | **152** |
| Passed | 152 |
| Failed | 0 |
| Skipped | 0 |
| Run-in-band wall time | ~13.5 s |

Reproduce: `npm test` → matches the figures in [TEST_COMPLETION_REPORT.md](TEST_COMPLETION_REPORT.md) §5.

### 5.2 Code coverage (Jest / istanbul)

Generated with `npm run coverage` → [reports/coverage/lcov-report/index.html](reports/coverage/lcov-report/index.html).

| File | % Stmts | % Branch | % Funcs | % Lines | Uncovered |
| --- | ---: | ---: | ---: | ---: | --- |
| **All files** | **98.26** | **82.60** | **100.00** | **98.23** | — |
| [app.js](app.js) | 96.15 | 100.00 | 100.00 | 96.15 | L24 (sequelize.authenticate catch — fault-injection only) |
| [errorHandlers.js](errorHandlers.js) | 100.00 | 83.33 | 100.00 | 100.00 | L18 (`||` fallback default — short-circuited) |
| [models/book.js](models/book.js) | 100.00 | 100.00 | 100.00 | 100.00 | — |
| [models/index.js](models/index.js) | 95.00 | 66.66 | 100.00 | 95.00 | L14 (`config.use_env_variable` — env-driven Sequelize config) |
| [routes/index.js](routes/index.js) | 100.00 | 100.00 | 100.00 | 100.00 | — |

Both uncovered lines are defensive paths only reachable under fault injection.

### 5.3 Mutation testing

Generated with `npm run mutation` → [reports/mutation/index.html](reports/mutation/index.html).

```
Mutants generated      : 164
Static (ignored)       :  64   (schema construction, see §4 WB-B6)
Mutants tested         : 100
  ├── Killed           :  82
  ├── Timeout (killed) :  13
  ├── Survived         :   0
  └── NoCoverage       :   5   (defensive paths in §3.2)
Mutation score         : 95.00 %
```

### 5.4 Cyclomatic complexity per handler

| Handler | Source | V(G) | Independent-path tests |
| --- | --- | ---: | ---: |
| `asyncHandler` | [routes/index.js:11-20](routes/index.js#L11-L20) | 2 | 2 |
| `GET /books` | [routes/index.js:28-88](routes/index.js#L28-L88) | 2 | 2 |
| `POST /books/new` | [routes/index.js:96-109](routes/index.js#L96-L109) | 3 | 3 |
| `GET /books/:id` | [routes/index.js:112-115](routes/index.js#L112-L115) | 2 | 2 |
| `POST /books/:id` | [routes/index.js:118-131](routes/index.js#L118-L131) | 3 | 3 |
| `POST /books/:id/delete` | [routes/index.js:134-138](routes/index.js#L134-L138) | 2 | 2 |
| `globalError` | [errorHandlers.js:13-23](errorHandlers.js#L13-L23) | 3 | 3 |
| **Σ paths** | | **17** | **17** |

### 5.5 Issue-list traceability (vs [whitebox_test_issues.md](../../docs/whitebox_test_issues.md))

| Issue | Description | Backing tests |
| ---: | --- | --- |
| 1 | `asyncHandler` and root redirect | basis_path · routes |
| 2 | `GET /books` no-search branch | routes · boundary_value · data_flow · statement_coverage |
| 3 | `GET /books` search branch (Op.or) | routes · decision_coverage · data_flow |
| 4 | `GET`/`POST /books/new` | routes · basis_path · decision_coverage · statement_coverage |
| 5 | `GET /books/:id` | routes · boundary_value · basis_path |
| 6 | `POST /books/:id` | routes · basis_path · decision_coverage |
| 7 | `POST /books/:id/delete` | routes · basis_path · boundary_value |
| 8 | `errorHandlers` | errorHandlers · basis_path · decision_coverage · data_flow |
| 9 | Book model + Sequelize bootstrap | models · boundary_value |
| 10 | Server startup and middleware wiring | server · statement_coverage |

All ten issues map to ≥ 1 dedicated technique file plus the behavioural-anchor suites.

### 5.6 Resource consumption

| Resource | Value |
| --- | --- |
| Jest full run (10 suites, 152 tests) | ~13.5 s |
| Jest coverage run | ~14.5 s |
| Stryker mutation run (100 tested mutants) | ~3 min 30 s |
| Engineer-days for the white-box engagement | ~1.5 days (writing) + ~0.5 day (this report) |

---

## 6. Test deliverables

All paths are relative to [whitebox/Library-DB-Express/](.), with cross-references to the project root [TestingProject/](../..) where relevant.

### 6.1 Plan and design artifacts

| Artifact | Location |
| --- | --- |
| Master test plan | [docs/test_plan.pdf](../../docs/test_plan.pdf) |
| White-box issue list (10 issues) | [docs/whitebox_test_issues.md](../../docs/whitebox_test_issues.md) |
| Report template | [docs/SE 2226 Test Completion Report Template.pdf](../../docs/SE%202226%20Test%20Completion%20Report%20Template.pdf) |

### 6.2 Test code

| Artifact | Location | Contents |
| --- | --- | --- |
| Issue-driven suites | [\_\_tests\_\_/routes.test.js](__tests__/routes.test.js), [models.test.js](__tests__/models.test.js), [errorHandlers.test.js](__tests__/errorHandlers.test.js), [server.test.js](__tests__/server.test.js) | Behaviour, model validation, middleware wiring. |
| Mutation-resistance suite | [\_\_tests\_\_/mutation.test.js](__tests__/mutation.test.js) | 11 robustness tests aimed at common mutators. |
| Statement coverage | [\_\_tests\_\_/statement_coverage.test.js](__tests__/statement_coverage.test.js) | 19 tests; every executable line. |
| Decision (branch) coverage | [\_\_tests\_\_/decision_coverage.test.js](__tests__/decision_coverage.test.js) | 18 tests; both T/F outcomes per decision. |
| Basis path testing | [\_\_tests\_\_/basis_path.test.js](__tests__/basis_path.test.js) | 17 tests; one per independent path; V(G) annotated. |
| Boundary value analysis | [\_\_tests\_\_/boundary_value.test.js](__tests__/boundary_value.test.js) | 24 tests for page/id/year/title/author/search boundaries. |
| Data-flow coverage | [\_\_tests\_\_/data_flow.test.js](__tests__/data_flow.test.js) | 16 tests covering def-use chains for `search`, `page`, `book`, `errors`, `err.status`, `err.message`. |

### 6.3 Tooling configuration

| Artifact | Location |
| --- | --- |
| Mutation runner config | [stryker.conf.js](stryker.conf.js) |
| Jest / npm scripts (`test`, `coverage`, `mutation`) | [package.json](package.json) |

### 6.4 Generated reports

| Artifact | Location | Generator |
| --- | --- | --- |
| Coverage HTML report | [reports/coverage/lcov-report/index.html](reports/coverage/lcov-report/index.html) | `npm run coverage` |
| Coverage lcov data | [reports/coverage/lcov.info](reports/coverage/lcov.info) | `npm run coverage` |
| Mutation HTML report | [reports/mutation/index.html](reports/mutation/index.html) | `npm run mutation` |

### 6.5 Documents

| Artifact | Location |
| --- | --- |
| **This report (29119-3 §7.4 — pre-existing white-box closeout)** | [WHITEBOX_TEST_REPORT.md](WHITEBOX_TEST_REPORT.md) — *this file* |
| Engagement closeout (29119-3 §7.4) | [TEST_COMPLETION_REPORT.md](TEST_COMPLETION_REPORT.md) |
| Project-level closeout (both sub-projects) | [docs/TEST_COMPLETION_REPORT.md](../../docs/TEST_COMPLETION_REPORT.md) |

---

## 7. Lessons learned

1. **One file per technique, not one mega-file.** Splitting tests by technique (`statement_coverage`, `decision_coverage`, `basis_path`, `boundary_value`, `data_flow`) made traceability between test plan §3.5 and the suite literal: a reviewer can `grep -l describe` per file and confirm each technique was exercised. Keep this convention.
2. **Lock to the file-name convention `*.test.js` early.** Five test files named `*_test.js` were silently invisible to Stryker for an entire run cycle (WB-D3). Conventions chosen by tooling are not optional; ratify them on the first day.
3. **`coverageAnalysis: 'off'` lies about static mutants.** With `off`, Stryker had no way to mark unkillable static mutants; they registered as "Survived" with `testsCompleted: 0`, suggesting fictional gaps in our tests. `coverageAnalysis: 'perTest'` + `ignoreStatic: true` produced an honest 95.00 %; the previous 90.67 % was not.
4. **Two equivalent mutants pointed at a real bug.** Both surviving mutants flipped `"New Book"` to `""` on a route local that reached no template. The "fix the test" instinct here was wrong; the right fix was to make `<title>` actually render the local in `layout.pug`, which improved the application *and* killed the mutants. Mutation testing surfaces dead code, not just weak tests.
5. **Validate against the runtime, not the spec.** Two test failures came from assertions rooted in plan-level expectations (`book.id === 1`, `"Please Provide a Value For Title"` on a *null* field) that did not match Sequelize's runtime behaviour. Probe the running system once before committing the assertion.
6. **Static analysis tools must follow the language.** PMD/CheckStyle do not apply to Node.js; an ESLint pass would have been the honest equivalent of test_plan.pdf §3.6 E4. Future plans should name techniques (SC, BC, basis path, DFT, mutation) rather than tools.
7. **OS-specific native modules are a reproducibility hazard.** Half a day was lost to a `sqlite3` binding compiled for Windows. CI should be the source of truth for `node_modules`; never share that directory across operating systems.

---

## Appendix A — Reproduce the figures in §5

```bash
# Setup (Linux / macOS, Node 18.x)
cd whitebox/Library-DB-Express
npm install
npm rebuild sqlite3   # rebuild native binding for the host OS

# §5.1 — 152-test Jest run (~13 s)
npm test

# §5.2 — Coverage report → reports/coverage/lcov-report/index.html
npm run coverage

# §5.3 — Stryker mutation run (~3.5 min) → reports/mutation/index.html
npm run mutation
```

Tooling versions: Node 18.19.1, Jest 30.3.0, Stryker 7.3.0, Sequelize 6.x, supertest 7.2.2.
