package ca.saboor.lumiguide;

import java.util.*;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

final class Handbook {
    record Page(String path, String title, String description, String keywords, String markdown, String icon) {
        net.kyori.adventure.key.Key sprite() { return ButtonIcon.guideSprite(icon); }
        net.kyori.adventure.text.Component content() { return Markdown.render(markdown); }
    }
    record Menu(String text, String href, List<Menu> items) {}
    record Feed(int version, List<Menu> menu, List<Page> pages) {
        Feed checked() {
            if (version != 1 || menu == null || pages == null || pages.isEmpty() || pages.size() > 2000)
                throw new IllegalArgumentException("Invalid handbook feed");
            Set<String> paths = new HashSet<>();
            for (Page p : pages) {
                if (p == null || p.path == null || !p.path.matches("/docs/(?:[a-z0-9-]+/)*") || !paths.add(p.path)
                        || p.title == null || p.title.isBlank() || p.markdown == null || p.markdown.length() > 500000)
                    throw new IllegalArgumentException("Invalid handbook page");
            }
            return this;
        }
        Page page(String path) { return pages.stream().filter(p -> p.path.equals(path)).findFirst().orElse(null); }
        List<Page> search(String query) {
            String[] terms = query.toLowerCase(Locale.ROOT).trim().split("\\s+");
            return pages.stream().filter(p -> Arrays.stream(terms).allMatch(t ->
                (p.title + " " + Objects.toString(p.description, "") + " " + Objects.toString(p.keywords, "") + " " + p.path)
                .toLowerCase(Locale.ROOT).contains(t))).sorted(Comparator.comparing(Page::title)).toList();
        }
    }
    static String render(String markdown) {
        return Markdown.plain(markdown);
    }
    static List<String> links(String markdown) {
        Set<String> paths = new LinkedHashSet<>();
        Parser.builder().build().parse(markdown).accept(new AbstractVisitor() {
            public void visit(Link n) {
                String path = n.getDestination().split("#", 2)[0];
                if (path.startsWith("/docs/")) paths.add(path.endsWith("/") ? path : path + "/");
                visitChildren(n);
            }
        });
        return List.copyOf(paths);
    }
}
