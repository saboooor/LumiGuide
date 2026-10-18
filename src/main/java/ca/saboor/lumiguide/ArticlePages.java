package ca.saboor.lumiguide;

import java.util.ArrayList;
import java.util.List;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Explicit, standalone page markers only; examples and nested Markdown stay literal. */
final class ArticlePages {
    private ArticlePages() {}

    static List<String> split(String markdown) {
        var document = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS).build().parse(markdown);
        String[] lines = markdown.split("\n", -1);
        int[] offsets = new int[lines.length];
        for (int i = 1; i < lines.length; i++) offsets[i] = offsets[i - 1] + lines[i - 1].length() + 1;
        List<String> pages = new ArrayList<>();
        int start = 0;
        boolean separated = false;
        for (var node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (!(node instanceof Paragraph) && !(node instanceof HtmlBlock)) continue;
            var spans = node.getSourceSpans();
            if (spans.size() != 1) continue;
            var span = spans.getFirst();
            String line = lines[span.getLineIndex()];
            if (!line.strip().equals("{/* pagebreak */}") && !line.strip().equals("<!-- pagebreak -->")) continue;
            int end = offsets[span.getLineIndex()];
            String section = markdown.substring(start, end);
            if (!section.isBlank()) pages.add(section);
            start = Math.min(markdown.length(), end + line.length() + 1);
            separated = true;
        }
        if (!separated) return List.of(markdown);
        String last = markdown.substring(start);
        if (!last.isBlank()) pages.add(last);
        return pages.isEmpty() ? List.of("") : List.copyOf(pages);
    }
}
