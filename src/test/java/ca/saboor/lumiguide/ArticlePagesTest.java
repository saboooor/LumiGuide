package ca.saboor.lumiguide;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArticlePagesTest {
    @Test void leavesUnmarkedDocumentsUnchangedRegardlessOfLength() {
        String source = "## Heading\n\nLong article.\n\n---\n\n".repeat(2000);
        assertEquals(List.of(source), ArticlePages.split(source));
    }

    @Test void separatesDaysAndPreservesMarkdownAndUnicode() {
        String first = "Intro\n\n## Oct 16 2026\n\n**Nether is open!** 🌸\n\n";
        String second = "\n## Oct 12 2026\n\n[Vehicles](/docs/vehicles/)\n";
        var pages = ArticlePages.split(first + "{/* pagebreak */}\n" + second);
        assertEquals(List.of(first, second), pages);
        assertFalse(Markdown.plain(pages.getFirst()).contains("Vehicles"));
        assertEquals(List.of(), Handbook.links(pages.getFirst()));
        assertEquals(List.of("/docs/vehicles/"), Handbook.links(pages.getLast()));
    }

    @Test void ignoresMarkersInExamplesAndNestedBlocks() {
        for (String source : List.of(
            "```md\n{/* pagebreak */}\n```\n",
            "~~~md\n<!-- pagebreak -->\n~~~\n",
            "    {/* pagebreak */}\n",
            "> {/* pagebreak */}\n",
            "- {/* pagebreak */}\n",
            "Use `{/* pagebreak */}` here.\n",
            "Paragraph\n{/* pagebreak */}\ncontinues.\n")) {
            assertEquals(List.of(source), ArticlePages.split(source), source);
        }
    }

    @Test void acceptsHtmlMarkersAndWindowsLineEndings() {
        assertEquals(List.of("One\r\n\r\n", "\r\nTwo"),
            ArticlePages.split("One\r\n\r\n<!-- pagebreak -->\r\n\r\nTwo"));
    }

    @Test void skipsEmptyPagesAndHandlesTrailingSeparators() {
        String marker = "{/* pagebreak */}\n\n";
        assertEquals(List.of("Text"), ArticlePages.split(marker + "Text\n\n" + marker + marker).stream().map(String::strip).toList());
        assertEquals(List.of(""), ArticlePages.split(marker + marker));
        assertEquals(List.of("Text\n\n"), ArticlePages.split("Text\n\n{/* pagebreak */}"));
        assertEquals(List.of(""), ArticlePages.split(""));
    }
}
