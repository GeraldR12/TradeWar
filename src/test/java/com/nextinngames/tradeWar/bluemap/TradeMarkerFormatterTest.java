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
                TradeRestriction.tariff("NewYork", 15.0, NOW + 60_000)
        ));

        String html = TradeMarkerFormatter.detailHtml(status, NOW);

        assertTrue(html.contains("Restrictions: 3"));
        assertTrue(html.contains("SANCTION"));
        assertTrue(html.contains("Brasilia"));
        assertTrue(html.contains("EMBARGO"));
        assertTrue(html.contains("UnitedKingdom"));
        assertTrue(html.contains("TARIFF"));
        assertTrue(html.contains("15% on all goods"));
    }

    @Test
    void permanentTariffShowsPermanentExpiry() {
        TradeStatus status = new TradeStatus("Havana", List.of(
                TradeRestriction.tariff("NewYork", 10.0, 0)));
        assertTrue(TradeMarkerFormatter.detailHtml(status, NOW).contains("Permanent"));
    }

    @Test
    void expiredTariffShowsExpiredLabel() {
        TradeStatus status = new TradeStatus("Havana", List.of(
                TradeRestriction.tariff("NewYork", 10.0, NOW - 1)));
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
