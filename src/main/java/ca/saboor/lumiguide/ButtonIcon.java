package ca.saboor.lumiguide;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.Bukkit;

/** Vanilla item sprites, using the same atlas selection as Lumistones. */
enum ButtonIcon {
    GUIDE("paper"), CATEGORY("map"), SEARCH("spyglass"), HANDBOOK("book"),
    RELATED("ender_pearl"), BACK("arrow"), CLOSE("barrier");

    final Key sprite;

    ButtonIcon(String item) { sprite = Key.key("minecraft:item/" + item); }

    Component label(String text) {
        return label(sprite, text);
    }

    static Key guideSprite(String icon) {
        // Only item sprite identifiers are accepted; missing or invalid metadata keeps the default.
        if (icon == null || !icon.matches("minecraft:item/[a-z0-9_]+")) return GUIDE.sprite;
        return Key.key(icon);
    }

    static Component label(Key sprite, String text) {
        String version = Bukkit.getServer() == null ? "1.21.10" : Bukkit.getMinecraftVersion();
        return Component.object(ObjectContents.sprite(sprite.value().startsWith("block/")
                ? Key.key("minecraft:blocks") : spriteAtlas(version), sprite))
            .append(Component.text(" " + text));
    }

    static Key spriteAtlas(String version) {
        // Item textures moved out of the blocks atlas in 1.21.11.
        String[] parts = version.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
        boolean separate = major > 1 || minor > 21 || minor == 21 && patch >= 11;
        return Key.key(separate ? "minecraft:items" : "minecraft:blocks");
    }
}
