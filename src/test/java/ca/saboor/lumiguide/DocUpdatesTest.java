package ca.saboor.lumiguide;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocUpdatesTest {
    private static final long NOW = Instant.parse("2026-10-18T14:00:00Z").toEpochMilli();
    private static Handbook.Page page(String path, String text) {
        return new Handbook.Page(path, path, null, null, text, null, null, "2026-10-09", "2026-10-17");
    }
    private static Handbook.Feed feed(Handbook.Page... pages) {
        return new Handbook.Feed(1, List.of(), List.of(pages)).checked();
    }

    @Test void firstFeedRecordsDatesAndNeverOpenedPlayersSeeNewDocs() {
        var feed = feed(page("/docs/", "Intro"), page("/docs/travel/", "Travel"));
        var state = DocUpdates.empty().refresh(feed, NOW);
        assertEquals(1, state.revision());
        assertEquals(NOW, state.updatedAt());
        assertEquals(2, state.unread(feed, 0).size());
        assertTrue(state.unread(feed, 0).stream().allMatch(DocUpdates.Unread::added));
        assertTrue(state.unread(feed, 0).stream().allMatch(item -> item.date().equals("2026-10-17")));
        assertTrue(state.unread(feed, state.revision()).isEmpty());
    }

    @Test void unchangedReloadOrFeedReorderingDoesNotCreateUpdates() {
        var a = page("/docs/", "Intro");
        var b = page("/docs/travel/", "Travel");
        var first = DocUpdates.empty().refresh(feed(a, b), NOW);
        assertEquals(first, first.refresh(feed(b, a), NOW + 10000));
    }

    @Test void distinguishesAddedFromUpdatedDocsSinceThePlayersLastOpen() {
        var original = feed(page("/docs/", "Intro"), page("/docs/travel/", "Travel"));
        var first = DocUpdates.empty().refresh(original, NOW);
        var nextFeed = feed(page("/docs/", "Changed intro"), page("/docs/travel/", "Travel"), page("/docs/changelog/", "New"));
        var next = first.refresh(nextFeed, NOW + 1000);
        var unread = next.unread(nextFeed, first.revision());
        assertEquals(2, unread.size());
        assertFalse(unread.stream().filter(item -> item.page().path().equals("/docs/")).findFirst().orElseThrow().added());
        assertTrue(unread.stream().filter(item -> item.page().path().equals("/docs/changelog/")).findFirst().orElseThrow().added());
        assertEquals("2026-10-18", unread.stream().filter(item -> !item.added()).findFirst().orElseThrow().date());
        assertTrue(next.unread(nextFeed, next.revision()).isEmpty());
    }

    @Test void catchesMultipleEditsOnTheSameDateWithoutMetadataChanges() {
        var first = DocUpdates.empty().refresh(feed(page("/docs/", "First")), NOW);
        var second = first.refresh(feed(page("/docs/", "Second")), NOW);
        var thirdFeed = feed(page("/docs/", "Third"));
        var third = second.refresh(thirdFeed, NOW);
        assertEquals(3, third.revision());
        assertEquals(1, third.unread(thirdFeed, second.revision()).size());
        assertFalse(third.unread(thirdFeed, second.revision()).getFirst().added());
    }

    @Test void aNewDocRemainsNewUntilThePlayerOpensTheHandbook() {
        var first = DocUpdates.empty().refresh(feed(page("/docs/", "First")), NOW);
        var second = first.refresh(feed(page("/docs/", "First"), page("/docs/changelog/", "New")), NOW);
        var nextFeed = feed(page("/docs/", "First"), page("/docs/changelog/", "Edited"));
        var third = second.refresh(nextFeed, NOW);
        assertTrue(third.unread(nextFeed, first.revision()).getFirst().added());
        assertFalse(third.unread(nextFeed, second.revision()).getFirst().added());
    }

    @Test void persistsHistoryAcrossRestartsAndDetectsOfflineChanges() {
        var firstFeed = feed(page("/docs/", "First"));
        var first = DocUpdates.empty().refresh(firstFeed, NOW);
        var gson = new Gson();
        var restored = gson.fromJson(gson.toJson(first), DocUpdates.class).checked();
        assertEquals(first, restored.refresh(firstFeed, NOW + 1000));
        var changed = feed(page("/docs/", "Changed while offline"));
        assertEquals(1, restored.refresh(changed, NOW + 1000).unread(changed, first.revision()).size());
        assertNotEquals(first.generation(), DocUpdates.empty().generation());
    }

    @Test void removalsDoNotSendNoticesAndReaddedDocsAreNew() {
        var a = page("/docs/", "First");
        var b = page("/docs/travel/", "Travel");
        var first = DocUpdates.empty().refresh(feed(a, b), NOW);
        var removed = first.refresh(feed(a), NOW + 1000);
        assertEquals(first.revision(), removed.revision());
        assertTrue(removed.unread(feed(a), first.revision()).isEmpty());
        var restored = removed.refresh(feed(a, b), NOW + 2000);
        assertTrue(restored.unread(feed(a, b), first.revision()).getFirst().added());
    }

    @Test void olderFeedsAndInvalidDatesUseTheDetectionDate() {
        var old = new Handbook.Page("/docs/", "Old feed", null, null, "Body", null, null);
        var invalid = new Handbook.Page("/docs/travel/", "Bad date", null, null, "Body", null, null, "not-a-date", "2099-01-01");
        var feed = feed(old, invalid);
        var state = DocUpdates.empty().refresh(feed, NOW);
        assertTrue(state.unread(feed, 0).stream().allMatch(item -> item.date().equals("2026-10-18")));
        assertThrows(IllegalArgumentException.class, () -> new Gson().fromJson("{}", DocUpdates.class).checked());
    }

    @Test void readsPublicationMetadataAndDetectsMetadataOnlyEdits() {
        var page = new Gson().fromJson("""
            {"path":"/docs/","title":"Handbook","markdown":"Body",
             "date_created":"2026-10-09","last_updated":"2026-10-17"}
            """, Handbook.Page.class);
        assertEquals("2026-10-09", page.date_created());
        assertEquals("2026-10-17", page.last_updated());
        var first = DocUpdates.empty().refresh(feed(page), NOW);
        var renamed = new Handbook.Page(page.path(), "Player handbook", null, null, page.markdown(), null, null,
            page.date_created(), page.last_updated());
        var nextFeed = feed(renamed);
        var next = first.refresh(nextFeed, NOW);
        assertEquals(1, next.unread(nextFeed, first.revision()).size());
        assertEquals("Player handbook", next.unread(nextFeed, first.revision()).getFirst().page().title());
        assertFalse(next.unread(nextFeed, first.revision()).getFirst().added());
    }
}
