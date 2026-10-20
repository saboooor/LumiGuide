package ca.saboor.lumiguide;

import com.google.gson.Gson;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LumiGuide extends JavaPlugin {
    record Button(net.kyori.adventure.key.Key icon, String text, Runnable action, boolean search) {
        Button(net.kyori.adventure.key.Key icon, String text, Runnable action) { this(icon, text, action, false); }
        Button(ButtonIcon icon, String text, Runnable action) { this(icon.sprite, text, action); }
        Component label() { return ButtonIcon.label(icon, text); }
    }
    static final String SEARCH_LABEL = "Search";
    static final String CLOSE_LABEL = "Close";
    private Handbook.Feed feed;
    private final AtomicBoolean fetching = new AtomicBoolean();
    private final Gson gson = new Gson();
    private BedrockForms bedrock;
    private org.bukkit.scheduler.BukkitTask refreshTask;
    private HttpClient http;
    private DocUpdates updates = DocUpdates.empty();
    private UpdateNotices notices;

    @Override public void onEnable() {
        saveDefaultConfig();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        bedrock = new BedrockForms(this);
        try { feed = gson.fromJson(Files.readString(cache()), Handbook.Feed.class).checked(); }
        catch (Exception ignored) { getLogger().info("No valid cached handbook; fetching website docs."); }
        try { updates = gson.fromJson(Files.readString(updateCache()), DocUpdates.class).checked(); }
        catch (Exception ignored) { getLogger().info("Starting handbook update history."); }
        if (feed != null) {
            updates = updates.refresh(feed, System.currentTimeMillis());
            try { saveUpdates(updates); }
            catch (java.io.IOException e) { getLogger().warning("Could not save handbook update history: " + e.getMessage()); }
        }
        notices = new UpdateNotices(this);
        getServer().getPluginManager().registerEvents(notices, this);
        if (feed != null) notices.loaded(feed, updates);
        Objects.requireNonNull(getCommand("guide")).setExecutor(this);
        getServer().getPluginManager().registerEvents(new GuideItem(this), this);
        schedule();
        refresh(null);
    }
    @Override public void onDisable() { if (http != null) http.shutdownNow(); }
    private Path cache() { return getDataFolder().toPath().resolve("handbook.json"); }
    private Path updateCache() { return getDataFolder().toPath().resolve("doc-updates.json"); }
    private void saveUpdates(DocUpdates history) throws java.io.IOException {
        Path tmp = updateCache().resolveSibling("doc-updates.json.tmp");
        Files.writeString(tmp, gson.toJson(history));
        Files.move(tmp, updateCache(), StandardCopyOption.REPLACE_EXISTING);
    }
    private void schedule() {
        if (refreshTask != null) refreshTask.cancel();
        long interval = Math.max(1, getConfig().getLong("refresh-minutes", 10)) * 1200;
        refreshTask = getServer().getScheduler().runTaskTimer(this, () -> refresh(null), interval, interval);
    }
    private void refresh(CommandSender sender) {
        if (!fetching.compareAndSet(false, true)) { if (sender != null) sender.sendMessage("A handbook refresh is already running."); return; }
        String source = getConfig().getString("source", "https://luminaramc.org/api/docs/");
        DocUpdates previousUpdates = updates;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                URI uri = URI.create(source);
                if (!Set.of("https", "http").contains(uri.getScheme())) throw new IllegalArgumentException("Source must use HTTP(S)");
                var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).header("Accept", "application/json").GET().build();
                var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                String json;
                try (var body = response.body()) {
                    if (response.statusCode() != 200) throw new IllegalStateException("Website returned HTTP " + response.statusCode());
                    byte[] bytes = body.readNBytes(8 * 1024 * 1024 + 1);
                    if (bytes.length > 8 * 1024 * 1024) throw new IllegalArgumentException("Feed exceeds 8 MiB");
                    json = new String(bytes, StandardCharsets.UTF_8);
                }
                Handbook.Feed loaded = gson.fromJson(json, Handbook.Feed.class).checked();
                DocUpdates nextUpdates = previousUpdates.refresh(loaded, System.currentTimeMillis());
                Path tmp = cache().resolveSibling("handbook.json.tmp");
                Files.writeString(tmp, json);
                Files.move(tmp, cache(), StandardCopyOption.REPLACE_EXISTING);
                saveUpdates(nextUpdates);
                sync(() -> {
                    try {
                        feed = loaded;
                        updates = nextUpdates;
                        notices.loaded(feed, updates);
                        if (sender != null) sender.sendMessage("Loaded " + loaded.pages().size() + " handbook pages.");
                    } finally { fetching.set(false); }
                });
            } catch (Exception e) {
                getLogger().warning("Handbook refresh failed; retaining cached docs: " + e.getMessage());
                sync(() -> {
                    try { if (sender != null) sender.sendMessage("Could not refresh docs. Check the server log; cached docs remain available."); }
                    finally { fetching.set(false); }
                });
            }
        });
    }
    void sync(Runnable task) { if (isEnabled()) getServer().getScheduler().runTask(this, task); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("fetch")) {
            if (!sender.hasPermission("lumiguide.fetch")) { sender.sendMessage("You do not have permission to fetch docs."); return true; }
            refresh(sender); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("lumiguide.reload")) { sender.sendMessage("You do not have permission to reload docs."); return true; }
            reloadConfig(); schedule(); refresh(sender); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Use /guide fetch or /guide reload from the console."); return true; }
        if (feed == null) { player.sendMessage("The handbook is loading. Try /guide again shortly."); return true; }
        if (args.length == 0) home(player);
        else {
            String query = String.join(" ", args);
            String path = query.startsWith("/docs/") ? query : "/docs/" + query;
            if (!path.endsWith("/")) path += "/";
            if (feed.page(path) != null) article(player, path); else results(player, query);
        }
        return true;
    }
    private void home(Player p) { category(p, "Player handbook", feed.menu(), null, null); }
    private void category(Player p, String title, List<Handbook.Menu> nodes, String overview, Runnable back) {
        Runnable current = () -> category(p, title, nodes, overview, back);
        List<Button> buttons = new ArrayList<>();
        Set<String> extraTargets = new HashSet<>();
        // The root handbook keeps its extra buttons inside its article, reached from the menu.
        var overviewPage = overview == null ? null : feed.page(overview);
        if (overviewPage != null && overviewPage.extra_buttons() != null) {
            for (var extra : feed.buttons(overviewPage)) {
                if (overviewPage.extra_buttons().stream().anyMatch(item -> item.href().equals(extra.target().path()))) {
                    extraTargets.add(extra.target().path());
                    buttons.add(new Button(extra.icon(), extra.label(), () -> article(p, extra.target().path(), current)));
                }
            }
        }
        if (overview != null && feed.page(overview) != null) buttons.add(new Button(feed.page(overview).sprite(), "Overview", () -> article(p, overview, current)));
        for (var n : nodes) {
            if (n.items() != null && !n.items().isEmpty()) buttons.add(new Button(feed.page(n.href()) == null ? ButtonIcon.CATEGORY.sprite : feed.page(n.href()).sprite(), n.text(), () -> category(p, n.text(), n.items(), n.href(), current)));
            else if (feed.page(n.href()) != null && !extraTargets.contains(n.href())) buttons.add(new Button(feed.page(n.href()).sprite(), n.text(), () -> article(p, n.href(), current)));
        }
        if (back != null) buttons.add(new Button(ButtonIcon.BACK, "Back", back));
        show(p, title, Component.text("Choose a category or guide."), buttons, "", current);
    }
    private void results(Player p, String query) { results(p, query, () -> home(p)); }
    private void results(Player p, String query, Runnable back) {
        Runnable current = () -> results(p, query, back);
        var matches = feed.search(query);
        List<Button> buttons = new ArrayList<>(matches.stream()
            .map(page -> new Button(page.sprite(), page.title(), () -> article(p, page.path(), current))).toList());
        buttons.add(new Button(ButtonIcon.BACK, "Back", back));
        // Editing a search retains its original return destination rather than stacking results screens.
        show(p, "Search: " + query, Component.text(matches.size() + " guides found."), buttons, query, back);
    }
    private void article(Player p, String path) { article(p, path, () -> home(p)); }
    private void article(Player p, String path, Runnable back) {
        article(p, path, back, 0);
    }
    private void article(Player p, String path, Runnable back, int requestedPage) {
        var page = feed.page(path);
        if (page == null) { home(p); return; }
        var sections = ArticlePages.split(page.markdown());
        int index = Math.clamp(requestedPage, 0, sections.size() - 1);
        var section = new Handbook.Page(page.path(), page.title(), page.description(), page.keywords(),
            sections.get(index), page.icon(), page.extra_buttons());
        Runnable current = () -> article(p, path, back, index);
        List<Button> buttons = new ArrayList<>();
        if (index > 0) buttons.add(new Button(ButtonIcon.PREVIOUS, "Previous page", () -> article(p, path, back, index - 1)));
        if (index + 1 < sections.size()) buttons.add(new Button(ButtonIcon.NEXT, "Next page", () -> article(p, path, back, index + 1)));
        for (var link : feed.buttons(section))
            buttons.add(new Button(link.icon(), link.label(), () -> article(p, link.target().path(), current)));
        String parent = path.substring(0, Math.max(0, path.lastIndexOf('/', path.length() - 2)) + 1);
        if (feed.page(parent) != null && !parent.equals(path)) buttons.add(new Button(feed.page(parent).sprite(), "Category overview", () -> article(p, parent, current)));
        buttons.add(new Button(ButtonIcon.BACK, "Back", back));
        buttons.add(new Button(ButtonIcon.SEARCH, "Search", () -> home(p)));
        String title = page.title() + (sections.size() > 1 ? " (" + (index + 1) + "/" + sections.size() + ")" : "");
        show(p, title, section.content(), buttons);
    }
    private void show(Player p, String title, String text, List<Button> buttons) {
        show(p, title, Component.text(text), buttons);
    }
    private void show(Player p, String title, Component text, List<Button> buttons) {
        show(p, title, text, buttons, null, () -> home(p));
    }
    private void show(Player p, String title, Component text, List<Button> buttons, String query, Runnable searchBack) {
        if (!p.isOnline() || !p.hasPermission("lumiguide.use")) return;
        List<Button> actions = new ArrayList<>(buttons);
        if (query != null) actions.addFirst(new Button(ButtonIcon.SEARCH.sprite, SEARCH_LABEL, () -> {}, true));
        actions.sort(Comparator.comparingInt(button ->
            button.icon().equals(ButtonIcon.BACK.sprite) ? 0 :
            button.icon().equals(ButtonIcon.SEARCH.sprite) ? 1 : 2));
        if (bedrock.isBedrock(p)) {
            boolean shown = query == null ? bedrock.show(p, title, Markdown.bedrock(text), actions)
                : bedrock.showSearch(p, title, Markdown.bedrock(text), actions, query, value -> results(p, value, searchBack));
            if (shown) notices.opened(p);
            return;
        }
        List<ActionButton> paperButtons = actions.stream().map(b -> ActionButton.create(b.label(), null, 200,
                DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player actor && actor.getUniqueId().equals(p.getUniqueId())) {
                        String value = b.search() ? Objects.toString(view.getText("query"), "") : "";
                        sync(() -> {
                            if (actor.isOnline() && actor.hasPermission("lumiguide.use")) {
                                if (b.search()) results(actor, value, searchBack); else b.action().run();
                            }
                        });
                    }
                }, ClickCallback.Options.builder().uses(1).build()))).toList();
        int navigationCount = (int) actions.stream().filter(button ->
            button.icon().equals(ButtonIcon.SEARCH.sprite) || button.icon().equals(ButtonIcon.BACK.sprite)).count();
        p.showDialog(Dialog.create(f -> f.empty().base(DialogBase.builder(Component.text(title)).pause(false)
            .body(List.of(DialogBody.plainMessage(text, 380)))
            .inputs(query == null ? List.of() : List.of(DialogInput.text("query", Component.text("Search handbook"))
                .initial(query).maxLength(100).build())).build())
            .type(DialogType.multiAction(spacedButtons(paperButtons, navigationCount),
                ActionButton.create(ButtonIcon.CLOSE.label(CLOSE_LABEL), null, 200, null), 2))));
        notices.opened(p);
    }

    private static ActionButton spacer() {
        return ActionButton.create(Component.text(" "), null, 1, null);
    }

    private static List<ActionButton> spacedButtons(List<ActionButton> buttons, int navigationCount) {
        List<ActionButton> spaced = new ArrayList<>();
        // Keep Search and Back on their own row, using Best Auction's inactive spacers.
        if (navigationCount > 0) {
            spaced.addAll(buttons.subList(0, navigationCount));
            if (navigationCount % 2 != 0) spaced.add(spacer());
            spaced.add(spacer()); spaced.add(spacer());
        }
        for (int i = navigationCount; i < buttons.size(); i += 2) {
            spaced.add(buttons.get(i));
            spaced.add(i + 1 < buttons.size() ? buttons.get(i + 1) : spacer());
        }
        return spaced;
    }
}
