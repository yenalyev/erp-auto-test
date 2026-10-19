package com.erp.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/** Classification advice inside an equipment item, independent of editable form fields. */
public class EquipmentKindHintSection {
    public static final String INTRO = "Засоби захисту та засоби побутового користування, пакування і т.д. не входять в обладнання";
    public static final String GLASSES = "Захисні окуляри - Засоби захисту, не обладнання";
    public static final String LAPTOP = "Ноутбук HP ZBook Power G7 - Обладнання";
    public static final String CONTACT = "У разі сумнівів або неясності зверніться до адміністратора Цукерочки.";

    private final Locator card;

    public EquipmentKindHintSection(Page page, int itemIndex) {
        card = page.locator("[data-slot='card']").nth(itemIndex);
    }

    public Locator expandButton() { return card.getByTestId("equipment-kind-hint-expand"); }
    public Locator collapseButton() { return card.getByTestId("equipment-kind-hint-collapse"); }
    public Locator expandedContainer() { return collapseButton().locator("xpath=../.."); }
    public Locator text(String text) { return card.getByText(text, new Locator.GetByTextOptions().setExact(true)); }
    public void expand() { expandButton().click(); }
    public void collapse() { collapseButton().click(); }
    public Locator card() { return card; }
}
