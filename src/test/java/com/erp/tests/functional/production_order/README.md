# Production groups API and UI tests

Business requirements, acceptance criteria and test cases in Ukrainian:
[`REQ-PO-GROUPS`](../../../../../../../../docs/REQ-PO-GROUPS-production-groups.md).
The same feature, 13 acceptance criteria and 32 business test cases are published
in production and local TCM. The JSON bundle and read-back verification are in `docs/`.

Run only this feature against dev:

```powershell
mvn test "-Dsuite=production-groups" "-Denv=dev" "-Dsuite.artifact.sweep=false" "-Dgoogle.sheets.enabled=false" "-Dtcm.enabled=false"
```

The suite creates isolated locations, resources, technological maps and production
orders through the API. It uses the configured ADMIN account. The RBAC scenario
uses `Business_Unit_Owner-ROLE` bound to a production group (the former director
role was consolidated into the business owner role). It creates two disposable
users with that role; it does not change shared user accounts.
Missing roles are failures, not skipped tests.

Cleanup deletes NEW orders, cancels generated orders, and deactivates created
maps, resources, users and locations. Cancelled generated orders remain as history.
The command disables the framework's environment-wide orphan sweep and external
report publishing. Test results are in `target/surefire-reports`.

The suite checks group routing, forbidden destinations, request idempotency,
progress, stale versions, atomic rejection, queue isolation, planner ownership,
request withdrawal and continuation after a group's answer. Business-rule cases
use ADMIN; only the explicit group-owner scenario checks owner permissions.

Browser coverage is in `ProductionGroupsUiTest` with a separate suite:

```powershell
mvn test "-Dsuite=production-groups-ui" "-Denv=dev" "-Dsuite.artifact.sweep=false" "-Dgoogle.sheets.enabled=false" "-Dtcm.enabled=false"
```

UI tests create two disposable business owners in independent browser contexts.
They cover the location checkbox and destination exclusion, partial delegation,
queue isolation, two-group planning in rounds through task generation, stale-plan
feedback with preserved input, explicit group-plan refresh, request progress and
sidebar/tab counts, and send/receive location selectors (TC-PG-031/032).
API calls arrange data and verify persisted outcomes; user
actions under test go through the browser. Screenshots are attached to Allure.
The suite is also included in `ui`, `production` and `regression`.

Inventory stock writes are checked by the API suite; other stock-writing paths,
concurrent responses from multiple group owners and destination rendering after a
request is completed still need additional scenarios.
