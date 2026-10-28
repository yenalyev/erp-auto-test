"""Report one category-filter suite without mixing environments or earlier runs."""
import argparse
import json
import shutil
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path

from report_tech_map_category_filter import IDS, ROOT, read_results


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-directory", required=True)
    parser.add_argument("--environment", required=True, choices=("dev", "staging"))
    parser.add_argument("--audit", default="docs/audits/2026-10-21")
    parser.add_argument("--ui-rerun")
    parser.add_argument("--framework-directory")
    args = parser.parse_args()
    run = ROOT / args.run_directory
    audit = ROOT / args.audit
    audit.mkdir(parents=True, exist_ok=True)
    rows, configurations = read_results(args.run_directory)
    assert rows, "No category-filter test results found"
    original_rows = list(rows)
    evidence_runs = [run]
    if args.ui_rerun:
        repeated, repeated_config = read_results(args.ui_rerun)
        assert len(repeated) == 6 and all("-UI-" in r["testCaseId"] for r in repeated)
        rows = [r for r in rows if "-UI-" not in r["testCaseId"]] + repeated
        configurations += repeated_config
        evidence_runs.append(ROOT / args.ui_rerun)
    counts = dict(Counter(row["status"] for row in rows))
    cleanup = not any(c["method"] == "cleanupCategories" for c in configurations)
    report = {"environment": args.environment, "suite": "tech-map-category-filter",
              "runDirectory": args.run_directory, "executions": len(rows), "counts": counts,
              "configurationFailures": configurations, "cleanupSuccessful": cleanup,
              "results": rows, "tcmImported": False}
    if args.ui_rerun:
        report["initialResults"] = original_rows
        report["uiRerun"] = args.ui_rerun
    if args.framework_directory:
        framework = ET.parse(ROOT / args.framework_directory / "surefire-reports/testng-results.xml").getroot()
        report["framework"] = {key: int(framework.get(key, "0"))
                               for key in ("total", "passed", "failed", "skipped")}
    stem = f"tech-map-category-{args.environment}"
    (audit / f"{stem}-results.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    counts_text = ", ".join(f"{status}: {count}" for status, count in sorted(counts.items()))
    lines = [f"# Техкарти — фільтр категорії виробу: {args.environment}", "",
             f"Дата прогону: {min(r['executedAt'] for r in rows)[:10]}. Набір: `tech-map-category-filter`.", "",
             f"Виконань: {len(rows)}. {counts_text}. Помилок setup/cleanup: {len(configurations)}.", "",
             "TCM: результати цього прогону не імпортовано.", "",
             "## Результати", ""]
    if args.ui_rerun:
        lines[8:8] = ["Початковий прогін: " + str(dict(Counter(r['status'] for r in original_rows))) + ". "
                      "У UI-002/003 виправлено неоднозначний локатор назви: пошук обмежено рядком карти. "
                      "У підсумку UI-результати замінені повним повтором усіх шести UI-виконань; "
                      "початкові результати збережено у JSON.", ""]
    if args.framework_directory:
        lines[8:8] = ["Framework: " + str(report["framework"]) + ".", ""]
    failures = {r["testCaseId"] for r in rows if r["status"] != "PASS"}
    if failures == {"TC-TM-CAT-API-007"} and not configurations:
        lines[8:8] = ["Єдиний дефект: статистика тегів не враховує категорію виробу й повертає тег карти "
                      "з іншої категорії. Решта API/UI-перевірок фільтра пройшла.", ""]
    for row in sorted(rows, key=lambda r: (r["testCaseId"], r["parameters"])):
        params = ", ".join(row["parameters"]) or "ADMIN"
        lines += [f"### {row['testCaseId']} ({params}) — {row['status']}", "",
                  f"Метод: `{row['method']}`; тривалість: {row['durationMs']} мс.", ""]
        if row["errorMessage"]:
            lines += ["```text", row["errorMessage"], "```", ""]
    if configurations:
        lines += ["## Помилки конфігурації", "",
                  "```json", json.dumps(configurations, ensure_ascii=False, indent=2), "```", ""]
    lines += ["## Докази", "",
              f"- TestNG XML: `{args.run_directory}/surefire-reports/testng-results.xml`.",
              f"- Maven log: `{args.run_directory}/maven.log`.",
              f"- [JSON результатів]({stem}-results.json).", "",
              "Очищення власних тестових даних: " + ("успішне." if cleanup else "помилка; див. конфігураційні результати."), ""]
    if args.ui_rerun:
        lines += [f"Повтор UI: `{args.ui_rerun}/surefire-reports/testng-results.xml`.", ""]
    if args.framework_directory:
        lines += [f"Framework: `{args.framework_directory}/surefire-reports/testng-results.xml`.", ""]
    evidence = audit / f"{stem}-evidence"
    evidence.mkdir(exist_ok=True)
    image_links = {}
    for run_source, filename in ((source, file) for source in evidence_runs
                                 for file in sorted((source / "allure-results").glob("*-result.json"))):
        result = json.loads(filename.read_text(encoding="utf-8"))
        case_id = next(("TC-TM-CAT-" + suffix for method, suffix in IDS.items()
                        if suffix.startswith("UI") and result.get("fullName", "").endswith("." + method)), None)
        if not case_id:
            continue
        role = next((p.get("value") for p in result.get("parameters", [])
                     if p.get("value") in ("ADMIN", "OWNER_1")), "ADMIN")
        for attachment in result.get("attachments", []):
            if attachment.get("type") == "image/png":
                destination = evidence / f"{case_id}-{role}.png"
                shutil.copy2(run_source / "allure-results" / attachment["source"], destination)
                image_links[destination.name] = f"![{case_id} {role}]({evidence.name}/{destination.name})"
                break
    for link in image_links.values():
        lines += [link, ""]
    (audit / f"{stem}-report.md").write_text(
        "\n".join(line.rstrip() for line in lines).rstrip() + "\n", encoding="utf-8")
    print(json.dumps({"executions": len(rows), "counts": counts,
                      "configurationFailures": len(configurations), "cleanupSuccessful": cleanup}, ensure_ascii=False))


if __name__ == "__main__":
    main()
