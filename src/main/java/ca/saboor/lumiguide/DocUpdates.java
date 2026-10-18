package ca.saboor.lumiguide;

import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;

/** Immutable, persisted content revisions; publication dates alone cannot distinguish same-day edits. */
record DocUpdates(String generation, long revision, long updatedAt, Map<String, Doc> documents) {
    record Doc(String fingerprint, long addedRevision, long revision, String date) {}
    record Unread(Handbook.Page page, boolean added, String date) {}

    static DocUpdates empty() { return new DocUpdates(UUID.randomUUID().toString(), 0, 0, Map.of()); }

    DocUpdates checked() {
        if (generation == null || revision < 0 || documents == null
            || documents.values().stream().anyMatch(doc -> doc == null || doc.fingerprint == null
                || doc.addedRevision < 1 || doc.revision < doc.addedRevision || doc.revision > revision || doc.date == null))
            throw new IllegalArgumentException("Invalid handbook update history");
        return new DocUpdates(generation, revision, updatedAt, Map.copyOf(documents));
    }

    DocUpdates refresh(Handbook.Feed feed, long now) {
        Map<String, Doc> next = new HashMap<>();
        boolean changed = false;
        for (var page : feed.pages()) {
            String fingerprint = fingerprint(page);
            var previous = documents.get(page.path());
            if (previous != null && previous.fingerprint.equals(fingerprint)) next.put(page.path(), previous);
            else {
                changed = true;
                next.put(page.path(), new Doc(fingerprint, previous == null ? revision + 1 : previous.addedRevision,
                    revision + 1, previous == null ? date(page, now) : today(now)));
            }
        }
        return new DocUpdates(generation, changed ? revision + 1 : revision, changed ? now : updatedAt, Map.copyOf(next));
    }

    List<Unread> unread(Handbook.Feed feed, long seenRevision) {
        return feed.pages().stream().filter(page -> {
            var doc = documents.get(page.path());
            return doc != null && doc.revision > seenRevision;
        }).map(page -> {
            var doc = documents.get(page.path());
            return new Unread(page, doc.addedRevision > seenRevision, doc.date);
        }).sorted(Comparator.comparing((Unread item) -> item.page.title(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(item -> item.page.path())).toList();
    }

    private static String fingerprint(Handbook.Page page) {
        try {
            byte[] json = new Gson().toJson(page).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String date(Handbook.Page page, long now) {
        String today = today(now);
        // On first discovery prefer the advertised date; later edits still receive a fresh revision.
        for (String candidate : new String[] { page.last_updated(), page.date_created() }) {
            try {
                if (candidate != null && !LocalDate.parse(candidate).isAfter(LocalDate.parse(today))) return candidate;
            } catch (DateTimeException ignored) { /* Older feeds may omit dates or use a non-ISO format. */ }
        }
        return today;
    }

    private static String today(long now) { return Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString(); }
}
