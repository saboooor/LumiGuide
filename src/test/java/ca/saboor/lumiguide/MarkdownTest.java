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
}
