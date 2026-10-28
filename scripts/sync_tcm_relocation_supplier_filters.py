#!/usr/bin/env python3
"""Idempotent local TCM sync; production uses tcm-ai-prod with payload()."""
from pathlib import Path
import re

import sync_tcm_relocation_batch_accounting as core

ROOT = Path(__file__).resolve().parent.parent
core.FEATURE_ID = "REQ-REL-SUPPLIER"
core.FEATURE_PARENT_ID = "REQ-REL-FILTERS"
core.FEATURE_TITLE = 'Додати фільтр "Постачальник" до блоку фільтрів на Видати/Отримати'
core.FEATURE_DESCRIPTION = "Властивість ресурсу, OR/AND, підсумки, пагінація, Excel та перемикання типу."
core.FEATURE_MODULE = "REL"
core.FEATURE_PRIORITY = "HIGH"
core.DOCUMENTATION_PATH = ROOT / "docs/REQ-REL-SUPPLIER.md"
core.ACCEPTANCE_CRITERIA = re.findall(
    r"^- \*\*(AC-\d+)\.\*\* (.+)$", core.DOCUMENTATION_PATH.read_text(encoding="utf-8"), re.M
)
METHODS = {
    "API": ["singleSupplierTrimsCargoAndPreservesQuantities",
            "multipleSuppliersUseOrAndUnknownValueMatchesNothing",
            "supplierIntersectsTheSameItemAndOtherFilters",
            "filteredTotalsAndPaginationUseTheWholeSelection",
            "excelAppliesSupplierAndIgnoresPageSize",
            "existingHistoryUsesCurrentSupplierAfterUpdateAndRemoval",
            "externalReceiptFiltersByResourcePropertyRatherThanSenderLocation"],
    "UI": ["optionsSearchAndSingleSupplierFilter", "multipleSuppliersAndSameItemIntersection",
           "removingChipsRestoresAllCargoAndReopeningClearsSelection",
           "equipmentOmitsSupplierAndResourceModeRestoresIt", "reopeningUsesUpdatedSupplierOfExistingHistory"],
}


def cases():
    source = (ROOT / "docs/RELOCATION_SUPPLIER_FILTERS_TEST_CASES.md").read_text(encoding="utf-8")
    result = []
    for match in re.finditer(
        r"^### (TC-REL-SUP-(API|UI)-(\d+)) — (.+?) \((AC-[^)]+)\)\n(.*?)(?=\n### |\n## |\Z)",
        source, re.M | re.S,
    ):
        test_id, layer, number, title, acs, body = match.groups()
        method = METHODS[layer][int(number) - 1]
        package = "functional.relocation" if layer == "API" else "ui"
        class_name = "RelocationSupplierFilters" + ("Api" if layer == "API" else "Ui") + "Test"
        java = ROOT / f"src/test/java/com/erp/tests/{package.replace('.', '/')}/{class_name}.java"
        code = java.read_text(encoding="utf-8")
        assert f'@TestCaseId("{test_id}")' in code and f"void {method}(" in code, test_id
        expected = re.search(r"Очікується: (.+)", body).group(1)
        actions = re.findall(r"^\d+\. (.+)$", body, re.M)
        assert actions, test_id
        result.append(dict(
            testId=test_id, featureId=core.FEATURE_ID, acKey=acs.split(",")[0], title=title,
            status="ACTIVE", priority="HIGH", severity="MAJOR", testType="FUNCTIONAL" if layer == "API" else "UI",
            description=f"Автотест: com.erp.tests.{package}.{class_name}#{method}. Критерії: {acs}. "
                        "Suite: relocation-supplier-filters, relocations, regression; UI також ui. "
                        "Вимоги й дані: docs/REQ-REL-SUPPLIER.md.",
            preconditions="ERP dev; ADMIN; власні дані RelocationSupplierFiltersFixture; Chrome для UI. "
                          "AppConfig містить МОУ та Інші. Власні відправник, одержувач, сьогоднішня дата.",
            testData="A1=МОУ/A/2, A2=Інші/A/3, B1=без властивості/B/5; CREATED, CANCELLED, FINISHED, "
                     "RETURNED, LOST; sibling і стара дата. Надходження по 100; дві моделі обладнання для UI.",
            expectedResult=expected, tags="relocation,supplier,filters,excel,automated",
            apiAutomationIds=[test_id] if layer == "API" else [],
            uiAutomationIds=[test_id] if layer == "UI" else [],
            steps=[dict(stepOrder=i, actionText=action, expectedText=expected) for i, action in enumerate(actions, 1)],
        ))
    assert len(result) == 12, len(result)
    return result


core.CASES = cases()


def payload():
    return dict(feature=dict(featureId=core.FEATURE_ID, parentFeatureId=core.FEATURE_PARENT_ID,
                             title=core.FEATURE_TITLE, description=core.FEATURE_DESCRIPTION,
                             documentation=core.DOCUMENTATION_PATH.read_text(encoding="utf-8"),
                             module=core.FEATURE_MODULE, priority=core.FEATURE_PRIORITY),
                criteria=[dict(featureId=core.FEATURE_ID, acKey=key, text=text) for key, text in core.ACCEPTANCE_CRITERIA],
                cases=core.CASES)


if __name__ == "__main__":
    raise SystemExit(core.main())
