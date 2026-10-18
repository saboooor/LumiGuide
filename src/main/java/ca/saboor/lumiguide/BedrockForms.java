package ca.saboor.lumiguide;

import org.bukkit.entity.Player;
import java.util.*;
import java.util.function.Consumer;

/** Optional bridge loaded through Floodgate's classloader, like Lumistones. */
final class BedrockForms {
    private final LumiGuide plugin;
    private ClassLoader loader;
    private Class<?> apiType;
    private Object api;
    BedrockForms(LumiGuide plugin) {
        this.plugin = plugin;
        var floodgate = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) return;
        try {
            loader = floodgate.getClass().getClassLoader();
            apiType = type("org.geysermc.floodgate.api.FloodgateApi");
            api = apiType.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) { failed(e); }
    }
    boolean isBedrock(Player p) {
        if (api == null) return false;
        try { return (boolean) apiType.getMethod("isFloodgatePlayer", UUID.class).invoke(api, p.getUniqueId()); }
        catch (ReflectiveOperationException e) { failed(e); return false; }
    }
    boolean show(Player p, String title, String content, List<LumiGuide.Button> buttons) {
        try {
            var form = type("org.geysermc.cumulus.form.SimpleForm");
            var builderType = type(form.getName() + "$Builder");
            Object builder = form.getMethod("builder").invoke(null);
            generic().getMethod("title", String.class).invoke(builder, title);
            builderType.getMethod("content", String.class).invoke(builder, content);
            for (var b : buttons) builderType.getMethod("button", String.class).invoke(builder, b.text());
            builderType.getMethod("button", String.class).invoke(builder, LumiGuide.CLOSE_LABEL);
            Consumer<Object> callback = response -> {
                try {
                    int index = ((Number) type("org.geysermc.cumulus.response.SimpleFormResponse").getMethod("clickedButtonId").invoke(response)).intValue();
                    if (index >= 0 && index < buttons.size()) run(p, buttons.get(index).action());
                } catch (ReflectiveOperationException e) { failed(e); }
            };
            return send(p, builder, callback);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { failed(e); fallback(p); return false; }
    }
    boolean showSearch(Player p, String title, String content, List<LumiGuide.Button> buttons,
                    String query, Consumer<String> search) {
        try {
            // Bedrock custom forms combine inputs with an action dropdown and Submit.
            var form = type("org.geysermc.cumulus.form.CustomForm");
            var builderType = type(form.getName() + "$Builder");
            Object builder = form.getMethod("builder").invoke(null);
            generic().getMethod("title", String.class).invoke(builder, title);
            builderType.getMethod("label", String.class).invoke(builder, content);
            builderType.getMethod("input", String.class, String.class, String.class)
                .invoke(builder, "Search handbook", "Title, description, or keywords", query);
            List<String> labels = new ArrayList<>(buttons.stream().map(LumiGuide.Button::text).toList());
            labels.add(LumiGuide.CLOSE_LABEL);
            builderType.getMethod("dropdown", String.class, List.class, int.class)
                .invoke(builder, "Action", labels, 0);
            Consumer<Object> callback = response -> {
                try {
                    var responseType = type("org.geysermc.cumulus.response.CustomFormResponse");
                    // Labels occupy a response slot, followed by the input and action dropdown.
                    String value = (String) responseType.getMethod("asInput", int.class).invoke(response, 1);
                    int selected = ((Number) responseType.getMethod("asDropdown", int.class).invoke(response, 2)).intValue();
                    if (selected < 0 || selected >= buttons.size()) return;
                    var button = buttons.get(selected);
                    String bounded = value == null ? "" : value.substring(0, Math.min(100, value.length()));
                    run(p, () -> { if (button.search()) search.accept(bounded); else button.action().run(); });
                } catch (ReflectiveOperationException e) { failed(e); }
            };
            return send(p, builder, callback);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { failed(e); fallback(p); return false; }
    }
    private boolean send(Player p, Object builder, Consumer<Object> callback) throws ReflectiveOperationException {
        generic().getMethod("validResultHandler", Consumer.class).invoke(builder, callback);
        boolean sent = (boolean) apiType.getMethod("sendForm", UUID.class, generic()).invoke(api, p.getUniqueId(), builder);
        if (!sent) fallback(p);
        return sent;
    }
    private void run(Player p, Runnable action) { plugin.sync(() -> { if (p.isOnline() && p.hasPermission("lumiguide.use")) action.run(); }); }
    private Class<?> generic() throws ClassNotFoundException { return type("org.geysermc.cumulus.form.util.FormBuilder"); }
    private Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, true, loader); }
    private void failed(Throwable e) { plugin.getLogger().warning("Floodgate handbook form unavailable: " + e.getMessage()); }
    private void fallback(Player p) { p.sendMessage("Could not open the handbook form. Read the docs at https://luminaramc.org/docs/ or try /guide again."); }
}
