"""Build a reproducible Markdown/JSON report from actual TestNG results."""
import argparse
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from collections import Counter
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IDS = {
    "singleCategoryUsesOutputOnly": "API-001",
    "multipleCategoriesReturnDistinctUnion": "API-002",
    "expandedParentIncludesChildren": "API-003",
    "categoryIntersectsOtherSearchFields": "API-004",
    "emptyResultClearAndStateTypeIsolation": "API-005",
    "paginationCountsFilteredMaps": "API-006",
    "tagStatisticsRespectCategory": "API-007",
    "singleCategoryFiltersOutput": "UI-001",
    "multipleSelectionToggleAndClear": "UI-002",
    "parentSelectsSubtreeAndChildCanBeRemoved": "UI-003",
    "searchIntersectionAndEmptyState": "UI-004",
    "selectionSurvivesReloadNavigationAndResetsPage": "UI-005",
}


def read_results(directory):
    source = ROOT / directory / "surefire-reports/testng-results.xml"
    tree = ET.parse(source).getroot()
    rows, configurations = [], []
    for cls in tree.findall(".//class"):
        if "TechMapCategoryFilter" not in cls.get("name", ""):
            continue
        for method in cls.findall("test-method"):
            if method.get("is-config") == "true":
                if method.get("status") != "PASS":
                    configurations.append({"method": method.get("name"), "status": method.get("status"),
                                           "error": (method.findtext("exception/message") or "").strip()})
                continue
            suffix = IDS.get(method.get("name"))
            if not suffix:
                continue
            ended = method.get("finished-at")
            parts = ended.split()
            executed = datetime.fromisoformat(parts[0].replace("Z", "+00:00"))
            if executed.tzinfo is None:
                offsets = {"EEST": 3, "EET": 2, "UTC": 0, "GMT": 0}
                assert len(parts) == 2 and parts[1] in offsets, f"Unknown TestNG timezone: {ended}"
                executed = executed.replace(tzinfo=timezone(timedelta(hours=offsets[parts[1]])))
            executed = executed.astimezone(timezone(timedelta(hours=3))).replace(tzinfo=None).isoformat()
            rows.append({"testCaseId": "TC-TM-CAT-" + suffix, "method": method.get("name"),
                         "parameters": [p.text.strip() for p in method.findall("params/param/value")],
                         "status": method.get("status"), "durationMs": int(method.get("duration-ms", "0")),
                         "executedAt": executed,
                         "errorMessage": (method.findtext("exception/message") or "").strip() or None,
                         "source": str(source.relative_to(ROOT)).replace("\\", "/")})
    return rows, configurations


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--main", required=True)
    parser.add_argument("--statistics", required=True)
    parser.add_argument("--framework", required=True)
    parser.add_argument("--audit", default="docs/audits/2026-10-21")
    parser.add_argument("--tcm-status", default="Результати не опубліковано; JSON payload підготовлено окремо.")
    args = parser.parse_args()
    audit = ROOT / args.audit
    audit.mkdir(parents=True, exist_ok=True)
    rows, configurations = read_results(args.main)
    retried, extra_config = read_results(args.statistics)
    assert len(rows) == 14, f"Expected 14 main invocations, found {len(rows)}"
    assert len(retried) == 1 and retried[0]["testCaseId"] == "TC-TM-CAT-API-007"
    configurations += extra_config
    # Replace only the statistics test; keep every ADMIN/OWNER invocation.
    effective = [row for row in rows if row["testCaseId"] != "TC-TM-CAT-API-007"] + retried
    framework_path = ROOT / args.framework / "surefire-reports/testng-results.xml"
    framework = ET.parse(framework_path).getroot()
    framework_counts = {k: int(framework.get(k, "0")) for k in ("total", "passed", "failed", "skipped")}
    completeness = framework.find(".//test-method[@name='allRunnableErpTestsAreRegisteredInRegressionSuite']")
    assert completeness is not None
    report = {"environment": "staging", "url": "https://stage.cukerochka.sbs", "date": "2026-10-21",
              "featureId": "REQ-MFG-001-03", "suite": "tech-map-category-filter",
              "counts": dict(Counter(row["status"] for row in effective)),
              "configurationFailures": configurations, "results": effective,
              "originalStatisticsAttempt": [r for r in rows if r["testCaseId"] == "TC-TM-CAT-API-007"],
              "framework": framework_counts, "regressionCompleteness": completeness.get("status"),
              "tcmPublication": args.tcm_status}
    (audit / "tech-map-category-staging-results.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    imports = []
    for case_id in sorted(set(r["testCaseId"] for r in effective)):
        group = [r for r in effective if r["testCaseId"] == case_id]
        status = "FAIL" if any(r["status"] == "FAIL" for r in group) else (
            "BLOCKED" if any(r["status"] == "SKIP" for r in group) else "PASS")
        imports.append({"testCaseId": case_id, "status": status,
                        "durationMs": sum(r["durationMs"] for r in group),
                        "executedAt": max(r["executedAt"] for r in group),
                        "errorMessage": "\n\n".join(
                            f"{','.join(r['parameters']) or 'default'}: {r['errorMessage']}"
                            for r in group if r["errorMessage"]) or None})
    payload = {"remoteRunId": "tech-map-category-staging-20261021-093546",
               "featureId": 12, "environment": "staging", "suite": "tech-map-category-filter",
               "runName": "Техкарти — Категорія виробу — staging 21.10.2026", "results": imports}
    (audit / "tech-map-category-tcm-payload.json").write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    lines = ["# Техкарти — фільтр категорії виробу: staging", "",
             "Дата: 21.10.2026. Середовище: https://stage.cukerochka.sbs. TCM: REQ-MFG-001-03, AC-05/AC-06.", "",
             f"12 автоматизованих кейсів, 14 виконань ADMIN/OWNER: {report['counts']}. Помилок setup/cleanup: {len(configurations)}.",
             f"Framework: {framework_counts}; повнота regression.xml: {completeness.get('status')}.", "",
             "TCM: " + args.tcm_status, "",
             "## Висновок", "",
             "На стейджі фільтр відсутній на екрані, а список API не застосовує categoryIds. "
             "Наприклад, для категорії B totalElements=4 замість 2; для порожньої категорії повертаються всі чотири контрольні карти. "
             "Тестовий OWNER додатково отримує 403 на список техкарт. Це не успішний прогін фічі.", "",
             "Статистику тегів повторено з ADMIN, щоб 403 OWNER не приховував перевірку категорії; "
             "результат повтору замінює лише API-007 у підсумку. Інші ADMIN/OWNER результати збережено окремо.", "",
             "Перший запуск у sandbox зупинився до сценаріїв через DNS/Playwright; "
             "основний запуск виконано поза sandbox з чинним VPN та встановленим Chrome. "
             "Framework також повторено поза sandbox для доступу до localhost.", "",
             "## Результати", ""]
    for row in sorted(effective, key=lambda r: (r["testCaseId"], r["parameters"])):
        lines += [f"### {row['testCaseId']} ({','.join(row['parameters']) or 'ADMIN'}) — {row['status']}", "",
                  f"Метод: `{row['method']}`; тривалість: {row['durationMs']} мс.", ""]
        if row["errorMessage"]:
            lines += ["```text", row["errorMessage"], "```", ""]
    if configurations:
        lines += ["## Помилки конфігурації", "", "```json", json.dumps(configurations, ensure_ascii=False, indent=2), "```", ""]
    lines += ["## Докази й повторний запуск", "",
              f"- Основний TestNG XML: `{args.main}/surefire-reports/testng-results.xml`.",
              f"- Повтор статистики: `{args.statistics}/surefire-reports/testng-results.xml`.",
              f"- Framework XML: `{args.framework}/surefire-reports/testng-results.xml`.",
              "- JSON результатів: [tech-map-category-staging-results.json](tech-map-category-staging-results.json).",
              "- Вимоги, кейси й команда запуску: [REQ-MFG-CATEGORY-FILTER](../../REQ-MFG-CATEGORY-FILTER.md).", "",
              "Власні карти/ресурси архівовано, власні категорії видалено; режим редагування локації відновлено. "
              "SUT tk/tk-ui не змінювався. Для перевірки реалізованої фічі потрібен стейдж із відповідною версією UI/API.", ""]
    allure = ROOT / "target/allure-results"
    matches = subprocess.run(["rg", "-l", "TechMapCategoryFilterUiTest", str(allure), "-g", "*-result.json"],
                             capture_output=True, text=True, check=False)
    evidence = audit / "tech-map-category-evidence"
    evidence.mkdir(exist_ok=True)
    for filename in matches.stdout.splitlines():
        result = json.loads(Path(filename).read_text(encoding="utf-8"))
        if result.get("stage") != "finished" or not result.get("attachments"):
            continue
        case_id = next(("TC-TM-CAT-" + suffix for method, suffix in IDS.items()
                        if suffix.startswith("UI") and result.get("fullName", "").endswith("." + method)), None)
        if not case_id:
            continue
        role = next((p.get("value", "") for p in result.get("parameters", []) if "ROLE" in p.get("value", "")
                     or p.get("value") in ("ADMIN", "OWNER_1")), "default")
        for attachment in result["attachments"]:
            if attachment.get("type") == "image/png":
                destination = evidence / f"{case_id}-{role}.png"
                shutil.copy2(allure / attachment["source"], destination)
                lines += [f"![{case_id} {role}](tech-map-category-evidence/{destination.name})", ""]
                break
    (audit / "tech-map-category-staging-report.md").write_text(
        "\n".join(line.rstrip() for line in lines).rstrip() + "\n", encoding="utf-8")
    print(json.dumps({"counts": report["counts"], "configurations": len(configurations),
                      "framework": framework_counts, "regressionCompleteness": completeness.get("status")}, ensure_ascii=False))


if __name__ == "__main__":
    main()
