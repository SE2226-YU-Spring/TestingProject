# Library-DB-Express White-Box Issue List

Issues derived from the source in [whitebox/Library-DB-Express/](../whitebox/Library-DB-Express/). One issue per logical unit of code.

## Team

All five members share the work equally. No separate reviewer role; whoever is not on an issue reviews it.

- Atakan Sezginer
- Batuhan Yerebasmaz
- Ege Çınar
- İsmet Sayğın Koç
- Osman Şahin Güler

## Test Issues

### 1. `asyncHandler` wrapper and root redirect

- Scope: [routes/index.js:11-25](../whitebox/Library-DB-Express/routes/index.js#L11-L25)
- Goal: verify the wrapper behavior and the root route.
- Test focus: callback resolves normally; callback throws (sync and async); GET `/` response status and `Location` header.
- Owners: Atakan Sezginer, İsmet Sayğın Koç

### 2. GET `/books` — list with no search

- Scope: [routes/index.js:28-88](../whitebox/Library-DB-Express/routes/index.js#L28-L88), the `else` branch
- Goal: cover the no-search path and pagination math.
- Test focus: default page; explicit page values; page beyond last page; `pageCount` math; `bookCount` against fixture rows; values rendered in the view model.
- Owners: Batuhan Yerebasmaz, Ege Çınar

### 3. GET `/books` — search branch across all four fields

- Scope: [routes/index.js:34-63](../whitebox/Library-DB-Express/routes/index.js#L34-L63), the `if (search)` branch
- Goal: cover every leg of the `Op.or` clause.
- Test focus: search matches by `title`, by `author`, by `genre`, by `year`; substring match; no-results case; various `page` values.
- Owners: Osman Şahin Güler, Atakan Sezginer

### 4. GET `/books/new` and POST `/books/new` (create)

- Scope: [routes/index.js:91-109](../whitebox/Library-DB-Express/routes/index.js#L91-L109)
- Goal: cover form rendering and the three branches of the create handler.
- Test focus: GET renders empty form; POST with a valid body; POST missing `title`; POST missing `author`; POST when the database raises a non-validation error.
- Owners: İsmet Sayğın Koç, Batuhan Yerebasmaz

### 5. GET `/books/:id` (detail)

- Scope: [routes/index.js:112-115](../whitebox/Library-DB-Express/routes/index.js#L112-L115)
- Goal: cover the detail handler.
- Test focus: known id; unknown id; non-numeric id.
- Owners: Ege Çınar, Osman Şahin Güler

### 6. POST `/books/:id` (update)

- Scope: [routes/index.js:118-131](../whitebox/Library-DB-Express/routes/index.js#L118-L131)
- Goal: cover the three branches of the update handler.
- Test focus: known id with valid body; known id with invalid body; known id when the database raises a non-validation error; unknown id; non-numeric id.
- Owners: Atakan Sezginer, Batuhan Yerebasmaz

### 7. POST `/books/:id/delete`

- Scope: [routes/index.js:134-138](../whitebox/Library-DB-Express/routes/index.js#L134-L138)
- Goal: cover the delete handler.
- Test focus: delete a known id and verify the row is gone; delete an unknown id; non-numeric id.
- Owners: İsmet Sayğın Koç, Ege Çınar

### 8. Error handlers — `fourOhFour` and `globalError`

- Scope: [errorHandlers.js](../whitebox/Library-DB-Express/errorHandlers.js)
- Goal: cover both branches of `globalError` and the `fourOhFour` 404 builder.
- Test focus: unknown route triggers `fourOhFour` and renders `page-not-found`; an error with `status === 404` renders `page-not-found`; an error with another status renders `error`; an error with no status defaults correctly; message formatting.
- Owners: Osman Şahin Güler, Atakan Sezginer

### 9. Book model validation and Sequelize bootstrap

- Scope: [models/book.js](../whitebox/Library-DB-Express/models/book.js), [models/index.js](../whitebox/Library-DB-Express/models/index.js)
- Goal: cover validators on the Book model and the model-loader logic.
- Test focus: valid records; empty / whitespace `title`; empty / whitespace `author`; values for `genre` and `year`; `models/index.js` loads `book.js`, registers it on `db`, calls `associate` when present, and selects the correct config based on `NODE_ENV`.
- Owners: Batuhan Yerebasmaz, İsmet Sayğın Koç

### 10. Server startup and middleware wiring

- Scope: [app.js](../whitebox/Library-DB-Express/app.js), [bin/www](../whitebox/Library-DB-Express/bin/www)
- Goal: cover the bootstrap path.
- Test focus: middleware mounted in expected order; error handlers registered last; IIFE behavior on auth success and on auth failure; `normalizePort` numeric, named-pipe, and negative cases; `onError` switch on `EACCES`, `EADDRINUSE`, and the default branch.
- Owners: Ege Çınar, Osman Şahin Güler

## Workload Check

| Member | Issues |
| --- | --- |
| Atakan Sezginer | 1, 3, 6, 8 |
| Batuhan Yerebasmaz | 2, 4, 6, 9 |
| Ege Çınar | 2, 5, 7, 10 |
| İsmet Sayğın Koç | 1, 4, 7, 9 |
| Osman Şahin Güler | 3, 5, 8, 10 |

## Notes

- Use an in-memory SQLite instance (or equivalent test fixture) to keep tests isolated from the real `library.db`.
- Test data must be synthetic; no real personal information.
- Defects found while executing these issues should be filed as separate Incident Reports.
