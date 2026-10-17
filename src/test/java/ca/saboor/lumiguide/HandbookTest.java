package ca.saboor.lumiguide;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HandbookTest {
    @Test void rendersMarkdownWithoutExecutingCommandsOrHtml() {
        String text = Handbook.render("## Travel\n\nUse **waystones** and `/home`.\n\n<script>bad()</script>\n\n[Help](/docs/travel/)\n");
        assertTrue(text.contains("Travel")); assertTrue(text.contains("Use waystones and /home."));
        assertFalse(text.contains("script")); assertTrue(text.contains("Help"));
    }
    @Test void loadsGuideIconsFromFeedAndSupportsOlderFeeds() {
        var gson = new com.google.gson.Gson();
        var page = gson.fromJson("{\"path\":\"/docs/travel/\",\"icon\":\"minecraft:item/ender_pearl\"}", Handbook.Page.class);
        assertEquals("minecraft:item/ender_pearl", page.sprite().asString());
        var legacy = gson.fromJson("{\"path\":\"/docs/travel/\"}", Handbook.Page.class);
        assertEquals("minecraft:item/paper", legacy.sprite().asString());
        assertEquals("minecraft:item/paper", ButtonIcon.guideSprite("invalid icon").asString());
        assertEquals("minecraft:item/paper", ButtonIcon.guideSprite("minecraft:block/stone").asString());
    }
    @Test void findsRelatedGuidesAndDeduplicatesAnchors() {
        assertEquals(List.of("/docs/travel/"), Handbook.links("[A](/docs/travel/#a) [B](/docs/travel) [C](https://example.org)"));
    }
    @Test void validatesFeedAndMatchesAllSearchTerms() {
        var p = new Handbook.Page("/docs/travel/", "Travel", "Waystones", "teleport XP", "Read", null);
        var feed = new Handbook.Feed(1, List.of(), List.of(p)).checked();
        assertEquals(List.of(p), feed.search("WAYSTONES xp"));
        assertTrue(feed.search("waystones money").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Handbook.Feed(2, List.of(), List.of(p)).checked());
        assertThrows(IllegalArgumentException.class, () -> new Handbook.Feed(1, List.of(), List.of(p, p)).checked());
    }
}
