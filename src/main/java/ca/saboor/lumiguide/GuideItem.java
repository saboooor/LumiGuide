package ca.saboor.lumiguide;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class GuideItem implements Listener {
    private final LumiGuide plugin;
    private final NamespacedKey key;
    private final NamespacedKey receivedKey;

    GuideItem(LumiGuide plugin) {
        this.plugin = plugin;
        key = new NamespacedKey(plugin, "handbook");
        receivedKey = new NamespacedKey(plugin, "handbook_received");
    }

    private boolean isGuide(ItemStack item) {
        return item != null && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (!plugin.getConfig().getBoolean("join-item.enabled", true)
            || !player.hasPermission("lumiguide.use")) return;
        if (player.getPersistentDataContainer().has(receivedKey, PersistentDataType.BYTE)) return;
        if (player.getInventory().firstEmpty() == -1) return;
        var item = new ItemStack(Material.BOOK);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Player handbook", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("Right-click to open the handbook", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        });
        if (player.getInventory().addItem(item).isEmpty()) {
            player.getPersistentDataContainer().set(receivedKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            || !isGuide(event.getItem())) return;
        event.setCancelled(true);
        if (event.getPlayer().hasPermission("lumiguide.use")) {
            plugin.sync(() -> {
                if (event.getPlayer().isOnline()) event.getPlayer().performCommand("guide");
            });
        }
    }
}
