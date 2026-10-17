package ca.saboor.lumiguide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.gfm.strikethrough.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Renders parsed Markdown as styled text for a scrollable article. */
final class Markdown {
    private static final java.util.regex.Pattern STYLED_SPAN = java.util.regex.Pattern.compile(
        "<span\\s+style=([\"'])(.*?)\\1\\s*>", java.util.regex.Pattern.CASE_INSENSITIVE);
    private record Run(String text, Style style, net.kyori.adventure.key.Key sprite) {
        Run(String text, Style style) { this(text, style, null); }
    }
    private final List<Run> runs = new ArrayList<>();

    private static Markdown parse(String source) {
        Markdown renderer = new Markdown();
        renderer.node(Parser.builder().extensions(List.of(TablesExtension.create(), StrikethroughExtension.create())).build().parse(source),
            Style.style(NamedTextColor.WHITE), 0);
        return renderer;
    }
    static String plain(String source) {
        return parse(source).runs.stream().map(Run::text).collect(java.util.stream.Collectors.joining()).strip();
    }
    static Component render(String source) {
        Markdown renderer = parse(source);
        if (renderer.runs.stream().allMatch(run -> run.text.isBlank())) return Component.text("No content yet.");
        Component content = Component.empty();
        for (Run run : renderer.runs) content = content.append(run.sprite == null
            ? Component.text(run.text, run.style) : ButtonIcon.label(run.sprite, run.text).style(run.style));
        return content;
    }

    /** Bedrock supports colors, bold, italics and obfuscation, but not underline/strikethrough. */
    static String bedrock(Component component) {
        var flattener = net.kyori.adventure.text.flattener.ComponentFlattener.basic().toBuilder()
            .mapper(net.kyori.adventure.text.ObjectComponent.class, object -> "").build();
        return LegacyComponentSerializer.builder().character('§').flattener(flattener).build()
            .serialize(component).replace("§n", "").replace("§m", "");
    }

    private void text(String text, Style style) {
        // Markdown is literal content, never legacy formatting codes or MiniMessage markup.
        runs.add(new Run(text.replace("§", ""), style));
    }
    private void children(Node parent, Style style, int depth) {
        List<Style> scopes = new ArrayList<>();
        scopes.add(style);
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            Style current = scopes.getLast();
            if (child instanceof HtmlInline html) {
                var opening = STYLED_SPAN.matcher(html.getLiteral());
                if (opening.matches()) scopes.add(spanStyle(opening.group(2), current));
                else if (html.getLiteral().equalsIgnoreCase("</span>") && scopes.size() > 1)
                    scopes.removeLast();
            } else node(child, current, depth);
        }
    }
    private void node(Node node, Style style, int depth) {
        if (node instanceof Text n) text(n.getLiteral(), style);
        else if (node instanceof Code n) text(n.getLiteral(), style.color(NamedTextColor.AQUA));
        else if (node instanceof Strikethrough) children(node, style.decorate(TextDecoration.STRIKETHROUGH), depth);
        else if (node instanceof StrongEmphasis) children(node, style.decorate(TextDecoration.BOLD), depth);
        else if (node instanceof Emphasis) children(node, style.decorate(TextDecoration.ITALIC), depth);
        else if (node instanceof Heading n) {
            children(n, style.color(n.getLevel() <= 2 ? NamedTextColor.GOLD : NamedTextColor.YELLOW).decorate(TextDecoration.BOLD), depth);
            text("\n\n", style);
        } else if (node instanceof Paragraph) {
            children(node, style, depth);
            text(node.getParent() instanceof ListItem ? "\n" : "\n\n", style);
        } else if (node instanceof BulletList || node instanceof OrderedList) {
            int number = node instanceof OrderedList ordered ? ordered.getMarkerStartNumber() : 0;
            for (Node item = node.getFirstChild(); item != null; item = item.getNext()) {
                text("  ".repeat(depth) + (node instanceof OrderedList ? number++ + ". " : "• "), style.color(NamedTextColor.GRAY));
                children(item, style, depth + 1);
            }
            if (!(node.getParent() instanceof ListItem)) text("\n", style);
        } else if (node instanceof BlockQuote) {
            text("▎ ", style.color(NamedTextColor.GRAY));
            children(node, style.color(NamedTextColor.GRAY).decorate(TextDecoration.ITALIC), depth);
        } else if (node instanceof FencedCodeBlock n) {
            text(n.getLiteral(), style.color(NamedTextColor.AQUA)); text("\n", style);
        } else if (node instanceof IndentedCodeBlock n) {
            text(n.getLiteral(), style.color(NamedTextColor.AQUA)); text("\n", style);
        } else if (node instanceof TableBlock) {
            // Column widths are unpredictable in dialogs. Present each row with its column labels.
            List<String> headers = new ArrayList<>();
            for (Node section = node.getFirstChild(); section != null; section = section.getNext()) {
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    int column = 0;
                    for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) {
                        if (section instanceof TableHead) {
                            Markdown header = new Markdown(); header.children(cell, style, depth);
                            headers.add(header.runs.stream().map(Run::text).collect(java.util.stream.Collectors.joining()));
                        } else {
                            if (column < headers.size() && !headers.get(column).isBlank())
                                text(headers.get(column) + ": ", style.color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
                            children(cell, style, depth); text("\n", style);
                        }
                        column++;
                    }
                    if (!(section instanceof TableHead)) text("\n", style);
                }
            }
        } else if (node instanceof Link n) {
            Style link = style.color(NamedTextColor.BLUE).decorate(TextDecoration.UNDERLINED)
                .hoverEvent(HoverEvent.showText(Component.text(n.getDestination().startsWith("/docs/") ? "Use the guide button below to follow this link" : n.getDestination())));
            try {
                URI uri = URI.create(n.getDestination());
                if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null)
                    link = link.clickEvent(ClickEvent.openUrl(uri.toString()));
            } catch (IllegalArgumentException ignored) { /* Render malformed destinations as labels. */ }
            children(node, link, depth);
            // Bedrock form text cannot contain clickable links; retain external URLs in the body.
            if (!n.getDestination().startsWith("/docs/")) text(" (" + n.getDestination() + ")", style.color(NamedTextColor.GRAY));
        } else if (node instanceof Image image && image.getDestination().matches("minecraft:(item|block)/[a-z0-9_]+")) {
            String label = plainCell(image).replace("§", "");
            if (label.isBlank()) label = image.getDestination().substring(image.getDestination().indexOf('/') + 1).replace('_', ' ');
            runs.add(new Run(label, style, net.kyori.adventure.key.Key.key(image.getDestination())));
        } else if (node instanceof Image) {
            text("[Image: ", style.color(NamedTextColor.GRAY)); children(node, style.color(NamedTextColor.GRAY), depth); text("]", style.color(NamedTextColor.GRAY));
        } else if (node instanceof SoftLineBreak) text(" ", style);
        else if (node instanceof HardLineBreak) text("\n", style);
        else if (node instanceof ThematicBreak) text("────────────────\n\n", style.color(NamedTextColor.DARK_GRAY));
        else if (!(node instanceof HtmlBlock) && !(node instanceof HtmlInline)) children(node, style, depth);
    }

    private static String plainCell(Node node) {
        Markdown renderer = new Markdown();
        renderer.children(node, Style.empty(), 0);
        return renderer.runs.stream().map(Run::text).collect(java.util.stream.Collectors.joining());
    }

    /** Only visual CSS properties are accepted; spans cannot add actions or custom fonts. */
    private static Style spanStyle(String css, Style base) {
        Style result = base;
        for (String declaration : css.split(";")) {
            String[] pair = declaration.split(":", 2);
            if (pair.length != 2) continue;
            String value = pair[1].strip().toLowerCase(java.util.Locale.ROOT);
            switch (pair[0].strip().toLowerCase(java.util.Locale.ROOT)) {
                case "color" -> {
                    TextColor color = value.startsWith("#") ? TextColor.fromHexString(value)
                        : NamedTextColor.NAMES.value(value);
                    if (color != null) result = result.color(color);
                }
                case "font-weight" -> {
                    if (value.equals("bold") || value.equals("700")) result = result.decorate(TextDecoration.BOLD);
                    else if (value.equals("normal") || value.equals("400")) result = result.decoration(TextDecoration.BOLD, false);
                }
                case "font-style" -> {
                    if (value.equals("italic")) result = result.decorate(TextDecoration.ITALIC);
                    else if (value.equals("normal")) result = result.decoration(TextDecoration.ITALIC, false);
                }
                case "text-decoration" -> {
                    for (String decoration : value.split("\\s+")) {
                        if (decoration.equals("underline")) result = result.decorate(TextDecoration.UNDERLINED);
                        else if (decoration.equals("line-through")) result = result.decorate(TextDecoration.STRIKETHROUGH);
                        else if (decoration.equals("none")) result = result
                            .decoration(TextDecoration.UNDERLINED, false).decoration(TextDecoration.STRIKETHROUGH, false);
                    }
                }
                case "--minecraft-obfuscated" -> {
                    if (value.equals("true") || value.equals("false"))
                        result = result.decoration(TextDecoration.OBFUSCATED, Boolean.parseBoolean(value));
                }
                default -> { /* Ignore unsupported CSS. */ }
            }
        }
        return result;
    }
}
