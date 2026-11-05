package com.bokaliai.terminal;

import org.junit.Test;

import static org.junit.Assert.*;

public class RootCatalogAuditGateTest {
    private static final String TOKEN = "123e4567-e89b-12d3-a456-426614174000";

    @Test
    public void acceptsOnlyExactStandaloneStartAndEndMarkers() {
        String output = "before\\n" + RootCatalogAuditGate.startMarker(TOKEN)
            + "\\nprofile\\n" + RootCatalogAuditGate.startMarker(TOKEN)
            + ":END\\nafter\\n";
        assertTrue(RootCatalogAuditGate.hasCompleteAudit(output, TOKEN));
    }

    @Test
    public void rejectsMissingStartOrEndMarker() {
        assertFalse(RootCatalogAuditGate.hasCompleteAudit(
            RootCatalogAuditGate.startMarker(TOKEN) + "\\nprofile\\n", TOKEN));
        assertFalse(RootCatalogAuditGate.hasCompleteAudit(
            "profile\\n" + RootCatalogAuditGate.startMarker(TOKEN) + ":END\\n", TOKEN));
    }

    @Test
    public void rejectsMarkersEmbeddedInOtherText() {
        String output = "prefix " + RootCatalogAuditGate.startMarker(TOKEN)
            + "\\nprofile\\n" + RootCatalogAuditGate.startMarker(TOKEN) + ":END suffix\\n";
        assertFalse(RootCatalogAuditGate.hasCompleteAudit(output, TOKEN));
    }

    @Test
    public void rejectsOutputFromAnotherAuditToken() {
        String other = "123e4567-e89b-12d3-a456-426614174001";
        String output = RootCatalogAuditGate.startMarker(other) + "\\n"
            + RootCatalogAuditGate.startMarker(other) + ":END\\n";
        assertFalse(RootCatalogAuditGate.hasCompleteAudit(output, TOKEN));
    }

    @Test
    public void rejectsMalformedTokensAndEmptyOutput() {
        assertEquals("", RootCatalogAuditGate.startMarker("not-a-uuid"));
        assertFalse(RootCatalogAuditGate.hasCompleteAudit("anything", "not-a-uuid"));
        assertFalse(RootCatalogAuditGate.hasCompleteAudit("", TOKEN));
        assertFalse(RootCatalogAuditGate.hasCompleteAudit(null, TOKEN));
    }
}
