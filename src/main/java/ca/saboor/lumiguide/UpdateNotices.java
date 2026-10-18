package ca.saboor.lumiguide;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread delivery and persistent per-player acknowledgement of handbook revisions. */
final class UpdateNotices implements Listener {
    private final LumiGuide plugin;
    private final NamespacedKey seenRevision;
    private final NamespacedKey seenGeneration;
    private final NamespacedKey openedAt;
    private final Map<UUID, Long> notified = new HashMap<>();
    private Handbook.Feed feed;
    private DocUpdates history;

    UpdateNotices(LumiGuide plugin) {
        this.plugin = plugin;
        seenRevision = new NamespacedKey(plugin, "handbook_seen_revision");
        seenGeneration = new NamespacedKey(plugin, "handbook_seen_generation");
        openedAt = new NamespacedKey(plugin, "handbook_opened_at");
    }

    void loaded(Handbook.Feed feed, DocUpdates history) {
        this.feed = feed;
        this.history = history;
        plugin.getServer().getOnlinePlayers().forEach(this::notifyPlayer);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> notifyPlayer(event.getPlayer()), 40);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { notified.remove(event.getPlayer().getUniqueId()); }

    void opened(Player player) {
        if (history == null) return;
        var data = player.getPersistentDataContainer();
        data.set(seenGeneration, PersistentDataType.STRING, history.generation());
        data.set(seenRevision, PersistentDataType.LONG, history.revision());
        data.set(openedAt, PersistentDataType.LONG, System.currentTimeMillis());
    }

    private void notifyPlayer(Player player) {
        if (history == null || !player.isOnline() || !player.hasPermission("lumiguide.use")) return;
        var data = player.getPersistentDataContainer();
        long seen = history.generation().equals(data.get(seenGeneration, PersistentDataType.STRING))
            ? data.getOrDefault(seenRevision, PersistentDataType.LONG, 0L) : 0;
        if (notified.getOrDefault(player.getUniqueId(), -1L) == history.revision()) return;
        var unread = history.unread(feed, seen);
        if (unread.isEmpty()) return;
        notified.put(player.getUniqueId(), history.revision());
        String latest = unread.stream().map(DocUpdates.Unread::date).max(String::compareTo).orElseThrow();
        player.sendMessage(Component.text("Handbook updates (latest: " + latest + ")", NamedTextColor.GOLD)
            .append(Component.text(" — open /guide to catch up.", NamedTextColor.YELLOW))
            .clickEvent(ClickEvent.runCommand("/guide")));
        for (var item : unread) {
            player.sendMessage(Component.text(item.added() ? "  + New: " : "  • Updated: ", NamedTextColor.AQUA)
                .append(Component.text(item.page().title(), NamedTextColor.WHITE))
                .append(Component.text(" (" + item.date() + ")", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(Component.text("Open " + item.page().title())))
                .clickEvent(ClickEvent.runCommand("/guide " + item.page().path())));
        }
    }
}
