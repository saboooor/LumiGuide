package ca.saboor.lumiguide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarkdownTest {
    @Test void rendersSpritesWithTextFallbackAndKeepsExamplesLiteral() {
        String source = "Craft ![Paper](minecraft:item/paper) with ![Obsidian](minecraft:block/obsidian).";
        var page = Markdown.render(source);
        var sprites = leaves(page).stream().filter(c -> c instanceof net.kyori.adventure.text.ObjectComponent).toList();
        assertEquals(2, sprites.size());
        var paper = (net.kyori.adventure.text.object.SpriteObjectContents)
            ((net.kyori.adventure.text.ObjectComponent) sprites.get(0)).contents();
        var obsidian = (net.kyori.adventure.text.object.SpriteObjectContents)
            ((net.kyori.adventure.text.ObjectComponent) sprites.get(1)).contents();
        assertEquals("minecraft:item/paper", paper.sprite().asString());
        assertEquals("minecraft:block/obsidian", obsidian.sprite().asString());
        assertEquals("minecraft:blocks", obsidian.atlas().asString());
        assertEquals("Craft Paper with Obsidian.", Markdown.plain(source));
        String bedrock = Markdown.bedrock(page);
        assertTrue(bedrock.contains("Paper")); assertTrue(bedrock.contains("Obsidian"));
        assertFalse(bedrock.contains("minecraft:"));
        assertFalse(leaves(Markdown.render("`![Paper](minecraft:item/paper)`")).stream()
            .anyMatch(c -> c instanceof net.kyori.adventure.text.ObjectComponent));
        assertFalse(leaves(Markdown.render("![Bad](minecraft:item/../../bad)")).stream()
            .anyMatch(c -> c instanceof net.kyori.adventure.text.ObjectComponent));
        assertTrue(Markdown.plain("![Photo](https://example.org/photo.png)").contains("[Image: Photo]"));
    }
    private static List<Component> leaves(Component root) {
        List<Component> result = new ArrayList<>(); result.add(root);
        root.children().forEach(child -> result.addAll(leaves(child))); return result;
    }
    private static Component text(Component root, String value) {
        return leaves(root).stream().filter(c -> c instanceof TextComponent t && t.content().equals(value)).findFirst().orElseThrow();
    }
    private static String plain(Component root) { return PlainTextComponentSerializer.plainText().serialize(root); }

    @Test void stylesHeadingsAndNestedInlineFormatting() {
        var page = Markdown.render("## Travel\n\n**Bold and *italic*** with `/home`.");
        assertEquals(NamedTextColor.GOLD, text(page, "Travel").color());
        assertEquals(TextDecoration.State.TRUE, text(page, "Travel").decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, text(page, "italic").decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, text(page, "italic").decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.AQUA, text(page, "/home").color());
        assertNull(text(page, "/home").clickEvent());
    }
    @Test void rendersLongArticlesWithStylesAndUnicodeIntact() {
        String source = "😀".repeat(2000);
        var article = Markdown.render("**" + source + "**");
        assertEquals(source, plain(article).strip());
        assertEquals(TextDecoration.State.TRUE, text(article, source).decoration(TextDecoration.BOLD));
        assertTrue(Markdown.bedrock(article).contains(source));
    }
    @Test void rendersEmptyArticlesWithPlaceholder() {
        assertEquals("No content yet.", plain(Markdown.render("")));
    }
    @Test void preservesOrderedAndNestedListsAndQuotes() {
        var page = Markdown.render("3. First\n4. Second\n   - Nested\n\n> Remember this\n");
        assertTrue(plain(page).contains("3. First\n4. Second\n  • Nested"));
        assertTrue(plain(page).contains("▎ Remember this"));
        assertEquals(NamedTextColor.GRAY, text(page, "Remember this").color());
    }
    @Test void onlyHttpLinksAreClickableAndMarkupStaysLiteral() {
        var page = Markdown.render("[Web](https://example.org) [Guide](/docs/travel/) [Unsafe](javascript:evil)\n\n`<red>literal</red> §k`\n\n<script>bad()</script>");
        assertEquals(ClickEvent.Action.OPEN_URL, text(page, "Web").clickEvent().action());
        assertNull(text(page, "Guide").clickEvent()); assertNull(text(page, "Unsafe").clickEvent());
        assertTrue(plain(page).contains("<red>literal</red> k")); assertFalse(plain(page).contains("bad()"));
        String bedrock = Markdown.bedrock(page);
        assertFalse(bedrock.contains("§n")); assertFalse(bedrock.contains("§k"));
    }
    @Test void rendersTablesAsLabeledRowsAndSupportsStrikethrough() {
        var page = Markdown.render("| Fuel | Units |\n| --- | --- |\n| **Coal** | 2 |\n| Lava | 5 |\n\n~~Old~~");
        assertTrue(plain(page).contains("Fuel: Coal\nUnits: 2\n\nFuel: Lava\nUnits: 5"));
        assertFalse(plain(page).contains("|"));
        assertEquals(TextDecoration.State.TRUE, text(page, "Coal").decoration(TextDecoration.BOLD));
        assertEquals(TextDecoration.State.TRUE, text(page, "Old").decoration(TextDecoration.STRIKETHROUGH));
        assertFalse(Markdown.bedrock(page).contains("§m"));
    }
    @Test void bedrockRetainsSupportedStyles() {
        var page = Markdown.render("## Heading\n\n**Bold** and *italic* and `code`");
        String bedrock = Markdown.bedrock(page);
        assertTrue(bedrock.contains("§6§lHeading")); assertTrue(bedrock.contains("§lBold"));
        assertTrue(bedrock.contains("§oitalic")); assertTrue(bedrock.contains("§bcode"));
    }

    @Test void rendersExplicitStylesAnywhereAndRestoresNestedScopes() {
        var page = Markdown.render("Before <span style=\"color: #ff5555\">Red **bold** <span style=\"color: #55ff55; font-style: italic\">Green</span> Red again</span> After");
        assertEquals(NamedTextColor.RED, text(page, "Red ").color());
        assertEquals(NamedTextColor.RED, text(page, "bold").color());
        assertEquals(TextDecoration.State.TRUE, text(page, "bold").decoration(TextDecoration.BOLD));
        assertEquals(NamedTextColor.GREEN, text(page, "Green").color());
        assertEquals(TextDecoration.State.TRUE, text(page, "Green").decoration(TextDecoration.ITALIC));
        assertEquals(NamedTextColor.RED, text(page, " Red again").color());
        assertEquals(NamedTextColor.WHITE, text(page, " After").color());
        assertNull(text(page, "Green").clickEvent());
    }

    @Test void stylesTableCellsOnlyWhenExplicitlyMarked() {
        var page = Markdown.render("| Code | Color |\n| --- | --- |\n| `&c` | Red |\n| `&a` | <span style=\"color: #55ff55\">Green</span> |");
        assertEquals(NamedTextColor.WHITE, text(page, "Red").color());
        assertEquals(NamedTextColor.GREEN, text(page, "Green").color());
        assertEquals(NamedTextColor.AQUA, text(page, "&c").color());
    }

    @Test void supportsVisualStylesAndLeavesCodeLiteral() {
        var page = Markdown.render("Samples: <span style='font-weight: bold; text-decoration: underline line-through; --minecraft-obfuscated: true; font-family: uniform; click: /op'>Sample</span> Plain\n\n`<span style=\"color: red\">literal</span>`\n\n```html\n<span style=\"color: red\">block</span>\n```");
        var sample = text(page, "Sample");
        for (var decoration : List.of(TextDecoration.BOLD, TextDecoration.UNDERLINED,
                TextDecoration.STRIKETHROUGH, TextDecoration.OBFUSCATED))
            assertEquals(TextDecoration.State.TRUE, sample.decoration(decoration));
        assertNull(sample.clickEvent()); assertNull(sample.font());
        assertEquals(NamedTextColor.WHITE, text(page, " Plain").color());
        assertTrue(plain(page).contains("<span style=\"color: red\">literal</span>"));
        assertTrue(plain(page).contains("<span style=\"color: red\">block</span>"));
        assertTrue(Markdown.bedrock(page).contains("§k"));
        assertFalse(Markdown.bedrock(page).contains("§n"));
        assertFalse(Markdown.bedrock(page).contains("§m"));
    }
}
