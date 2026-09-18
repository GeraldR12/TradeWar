package com.nextinngames.tradeWar.bluemap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeMarkerFormatterTest {

    private static final long NOW = 1_000_000_000L;

    @Test
    void markerIdIsLowercaseAndPrefixed() {
        assertEquals("town-havana", TradeMarkerFormatter.markerId("Havana"));
    }

    @Test
    void labelIncludesTownName() {
        TradeStatus status = new TradeStatus("Havana", List.of(TradeRestriction.sanction("Brasilia")));
        assertTrue(TradeMarkerFormatter.label(status).contains("Havana"));
    }

    @Test
    void detailHtmlListsAllRestrictions() {
        TradeStatus status = new TradeStatus("Havana", List.of(
                TradeRestriction.sanction("Brasilia"),
                TradeRestriction.embargo("UnitedKingdom"),
                TradeRestriction.tariff("NewYork", "import", "IRON_INGOT", 15.0, NOW + 60_000)
        ));

        String html = TradeMarkerFormatter.detailHtml(status, NOW);

        assertTrue(html.contains("Restrictions: 3"));
        assertTrue(html.contains("SANCTION"));
        assertTrue(html.contains("Brasilia"));
        assertTrue(html.contains("NATION EMBARGO"));
        assertTrue(html.contains("UnitedKingdom"));
        assertTrue(html.contains("IMPORT TARIFF"));
        assertTrue(html.contains("IRON_INGOT"));
        assertTrue(html.contains("15"));
    }

    @Test
    void permanentTariffShowsPermanentExpiry() {
        TradeStatus status = new TradeStatus("Havana", List.of(
                TradeRestriction.tariff("NewYork", "export", "ALL", 10.0, 0)));
        assertTrue(TradeMarkerFormatter.detailHtml(status, NOW).contains("Permanent"));
    }

    @Test
    void expiredTariffShowsExpiredLabel() {
        TradeStatus status = new TradeStatus("Havana", List.of(
                TradeRestriction.tariff("NewYork", "export", "ALL", 10.0, NOW - 1)));
        assertTrue(TradeMarkerFormatter.detailHtml(status, NOW).contains("Expired"));
    }

    @Test
    void htmlEscapesTownAndTownNames() {
        TradeStatus status = new TradeStatus("<Havana>", List.of(TradeRestriction.sanction("A&B")));
        String html = TradeMarkerFormatter.detailHtml(status, NOW);
        assertTrue(html.contains("&lt;Havana&gt;"));
        assertTrue(html.contains("A&amp;B"));
    }
}
