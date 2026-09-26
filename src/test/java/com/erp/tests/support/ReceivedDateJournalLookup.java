package com.erp.tests.support;

import com.erp.enums.UserRole;
import com.erp.fixtures.RelocationFixture;
import com.erp.models.query.RelocationJournalQuery;
import com.erp.models.response.RelocationResponse;

/** Searches all matching journal pages; old selected dates may fall beyond the first page. */
public final class ReceivedDateJournalLookup {

    private ReceivedDateJournalLookup() {
    }

    public static RelocationResponse byDescription(RelocationFixture fixture,
                                                   UserRole role,
                                                   Long storageId,
                                                   Long resourceId,
                                                   String marker) {
        RelocationJournalQuery query = RelocationJournalQuery.builder()
                .storageId(storageId)
                .perspective(RelocationJournalQuery.Perspective.RECEIVED)
                .productId(resourceId)
                .pageSize(100)
                .build();
        long total = fixture.getJournalTotalElements(query, role);
        int pages = (int) Math.ceil((double) total / 100);
        for (int page = 0; page < pages; page++) {
            RelocationResponse match = fixture.getJournalPage(query.toBuilder().page(page).build(), role)
                    .stream()
                    .filter(row -> row.getDescription() != null && row.getDescription().contains(marker))
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                return match;
            }
        }
        return null;
    }
}
