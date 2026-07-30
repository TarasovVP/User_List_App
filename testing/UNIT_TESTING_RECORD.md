# Unit testing module record

## Scope

The aggregated Kover debug report includes production classes and local JVM tests
from `:app` and `:settings`. Kover does not instrument Android on-device tests, so
instrumented tests are run and reported separately. `:core:navigation` is excluded
because it contains serializable navigation contracts without local tests;
`:feature:account` is outside the module's requested aggregation scope.

Generated Hilt/BuildConfig/serializer classes, DI modules, Android entry points,
navigation wiring, and Compose UI are excluded. Their behavior is primarily
generated, framework-driven, or covered by the separate instrumented/screenshot
suite. Domain, repository, storage, networking, worker, coordinator, and ViewModel
logic remain in coverage scope.

## Multi-word search: red-green-refactor record

1. Red: add examples requiring `Ada Analytical` to match across name and company,
   requiring every term, tolerating repeated whitespace, and preserving
   case-insensitive matching.
2. Green: split the trimmed query on one-or-more whitespace characters and require
   every non-empty term to match at least one of full name, email, or company.
3. Refactor: compile the whitespace expression once, name the term/field concepts,
   retain the existing favorite filter and comparator, then add generated
   properties covering combinations and ordering.

Kotest reports the random seed and shrunk counterexample on property failure.
Re-running the test with the reported seed reproduces the generated sequence.

## Commands and results

Validated on July 30, 2026:

- `./gradlew test` — passed.
- `./gradlew :koverHtmlReportAggregatedDebug :koverXmlReportAggregatedDebug :koverVerifyAggregatedDebug`
  — passed. Aggregated line coverage after the documented filters is 44.38%
  (932 of 2,100 lines).
- The verification floor is 40%. It is just below the measured baseline, catches a
  meaningful regression, and avoids a brittle threshold equal to the current result.
- HTML report: `build/reports/kover/htmlAggregatedDebug/index.html`.
- XML report: `build/reports/kover/reportAggregatedDebug.xml`.
- `./gradlew lint` — passed.
- `./gradlew :app:assembleDebug :app:bundleRelease` — passed.
- `./gradlew :app:connectedDebugAndroidTest :settings:connectedDebugAndroidTest`
  — passed on `Medium_Phone(AVD) - 13`: 26 app tests and 1 settings test.
