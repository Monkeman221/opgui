package com.example.opgui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class OpScreen extends Screen {
    private record Target(String name, String label, PlayerListEntry entry) {}

    private record Act(String label, String cmd, int color, boolean confirm) {}

    private record Chip(String id, int x, int y, int w) {}

    private static final int PW = 344;          // panel width
    private static final int PH = 236;          // panel height
    private static final int PAD = 8;
    private static final int CW = PW - PAD * 2; // content width
    private static final int CONTENT_Y = 76;    // content starts this far below the panel top

    private static final int TAB_PLAYERS = 0;
    private static final int TAB_ITEMS = 1;
    private static final int TAB_SERVER = 2;

    private static final String SEARCH_HINT = "Search items (e.g. diamond)";

    private static List<String> itemCache;
    private static String selectedItem = "minecraft:diamond";
    private static String selectedEnch = "sharpness";

    private final String aimedPlayer; // player you were looking at when you opened the menu (may be null)
    private final List<Dropdown<?>> dropdowns = new ArrayList<>();
    private final List<FlatButton> buttons = new ArrayList<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();
    private final LinkedHashMap<String, Integer> chosen = new LinkedHashMap<>(); // enchantment id -> level

    private Dropdown<Target> playerDropdown;
    private Dropdown<String> itemDropdown;
    private Dropdown<Enchants.Ench> enchDropdown;
    private TextFieldWidget searchField;
    private TextFieldWidget countField;
    private TextFieldWidget levelField;
    private TextFieldWidget commandField;
    private FlatButton[] tabButtons;
    private FlatButton unbreakableButton;

    // state that survives window resizes
    private int tab = TAB_PLAYERS;
    private String targetName = null;
    private String searchText = "";
    private String countText = "1";
    private String levelText = "";
    private String commandText = "";
    private boolean unbreakable = false;

    private int panelLeft;
    private int panelTop;
    private String status = "";
    private int statusColor = Theme.MUTED;

    public OpScreen(String aimedPlayer) {
        super(Text.literal("OP Menu"));
        this.aimedPlayer = aimedPlayer;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        dropdowns.clear();
        buttons.clear();

        panelLeft = (width - PW) / 2;
        panelTop = Math.max(2, (height - PH) / 2);
        final int left = panelLeft + PAD;
        final int top = panelTop;
        final int cy = top + CONTENT_Y;

        // --- target player (shown on every tab) ---
        playerDropdown = new Dropdown<Target>(textRenderer, left, top + 22, 206, 8,
                Target::label, this::drawHead, t -> targetName = t.name());
        playerDropdown.setPlaceholder("Target player");
        dropdowns.add(playerDropdown);
        refreshPlayers();

        addButton(-1, left + 210, top + 22, 118, 20, "Refresh players", Theme.GRAY, this::refreshPlayers);

        // --- tabs ---
        tabButtons = new FlatButton[3];
        String[] tabNames = {"Players", "Give Items", "Server"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            tabButtons[i] = addButton(-1, left + i * 110, top + 48, i == 2 ? 108 : 106, 18,
                    tabNames[i], Theme.BTN, () -> setTab(index));
        }

        // --- tab 0: actions on the selected player ---
        addGrid(TAB_PLAYERS, left, cy, true, List.of(
                new Act("Op", "op %t", Theme.GREEN, false),
                new Act("Deop", "deop %t", Theme.ORANGE, false),
                new Act("Kick", "kick %t", Theme.RED, false),
                new Act("Ban", "ban %t", Theme.RED, true),
                new Act("Survival", "gamemode survival %t", Theme.BLUE, false),
                new Act("Creative", "gamemode creative %t", Theme.BLUE, false),
                new Act("Adventure", "gamemode adventure %t", Theme.BLUE, false),
                new Act("Spectator", "gamemode spectator %t", Theme.BLUE, false),
                new Act("Heal", "effect give %t minecraft:instant_health 1 10 true", Theme.GREEN, false),
                new Act("Feed", "effect give %t minecraft:saturation 1 20 true", Theme.GREEN, false),
                new Act("Clear inv", "clear %t", Theme.ORANGE, true),
                new Act("Kill", "kill %t", Theme.RED, true),
                new Act("TP to me", "tp %t @s", Theme.PURPLE, false),
                new Act("TP to them", "tp @s %t", Theme.PURPLE, false),
                new Act("Whitelist +", "whitelist add %t", Theme.TEAL, false),
                new Act("Whitelist -", "whitelist remove %t", Theme.TEAL, false)));

        // --- tab 1: give items (+ enchantments) ---
        searchField = addSelectableChild(new TextFieldWidget(textRenderer, left, cy, 206, 20, Text.literal("Search")));
        searchField.setMaxLength(64);
        searchField.setText(searchText);
        searchField.setSuggestion(searchText.isEmpty() ? SEARCH_HINT : null);
        searchField.setChangedListener(this::onSearchChanged);

        countField = addSelectableChild(new TextFieldWidget(textRenderer, left + 210, cy, 118, 20, Text.literal("Amount")));
        countField.setMaxLength(4);
        countField.setTextPredicate(s -> s.matches("\\d*"));
        countField.setText(countText);
        countField.setSuggestion(countText.isEmpty() ? "Amount" : null);
        countField.setChangedListener(s -> {
            countText = s;
            countField.setSuggestion(s.isEmpty() ? "Amount" : null);
        });

        itemDropdown = new Dropdown<String>(textRenderer, left, cy + 22, 206, 5,
                OpScreen::prettyId, (ctx, id, x, y) -> ctx.drawItem(iconFor(id), x, y), id -> selectedItem = id);
        itemDropdown.setPlaceholder("Pick an item");
        dropdowns.add(itemDropdown);
        applyFilter();

        addButton(TAB_ITEMS, left + 210, cy + 22, 118, 20, "Give", Theme.GREEN, this::giveSelected);

        unbreakableButton = addButton(TAB_ITEMS, left + CW - 44 - 4 - 104, cy + 47, 104, 14,
                "", Theme.GRAY, () -> {
                    unbreakable = !unbreakable;
                    refreshUnbreakableLabel();
                });
        refreshUnbreakableLabel();
        addButton(TAB_ITEMS, left + CW - 44, cy + 47, 44, 14, "Clear", Theme.RED, () -> {
            chosen.clear();
            setStatus("Cleared all enchantments.", Theme.MUTED);
        });

        enchDropdown = new Dropdown<Enchants.Ench>(textRenderer, left, cy + 64, 206, 6,
                e -> e.name() + "  (max " + Enchants.roman(e.max()) + ")", null, this::onEnchSelected);
        enchDropdown.setPlaceholder("Pick an enchantment");
        enchDropdown.setOptions(Enchants.ALL, Enchants.indexOf(selectedEnch));
        dropdowns.add(enchDropdown);

        levelField = addSelectableChild(new TextFieldWidget(textRenderer, left + 210, cy + 64, 46, 20, Text.literal("Level")));
        levelField.setMaxLength(3);
        levelField.setTextPredicate(s -> s.matches("\\d*"));
        if (levelText.isEmpty()) levelText = String.valueOf(Enchants.byId(selectedEnch).max());
        levelField.setText(levelText);
        levelField.setChangedListener(s -> levelText = s);

        addButton(TAB_ITEMS, left + 260, cy + 64, 68, 20, "Add", Theme.BLUE, this::addEnchant);

        // --- tab 2: server-wide commands ---
        addGrid(TAB_SERVER, left, cy, false, List.of(
                new Act("Day", "time set day", Theme.BLUE, false),
                new Act("Noon", "time set noon", Theme.BLUE, false),
                new Act("Night", "time set night", Theme.BLUE, false),
                new Act("Midnight", "time set midnight", Theme.BLUE, false),
                new Act("Sunny", "weather clear", Theme.TEAL, false),
                new Act("Rain", "weather rain", Theme.TEAL, false),
                new Act("Thunder", "weather thunder", Theme.TEAL, false),
                new Act("Save all", "save-all", Theme.GRAY, false),
                new Act("Peaceful", "difficulty peaceful", Theme.ORANGE, false),
                new Act("Easy", "difficulty easy", Theme.ORANGE, false),
                new Act("Normal", "difficulty normal", Theme.ORANGE, false),
                new Act("Hard", "difficulty hard", Theme.ORANGE, false),
                new Act("Keep inv ON", "gamerule keep_inventory true", Theme.GREEN, false),
                new Act("Keep inv OFF", "gamerule keep_inventory false", Theme.RED, false),
                new Act("Mob grief ON", "gamerule mob_griefing true", Theme.GREEN, false),
                new Act("Mob grief OFF", "gamerule mob_griefing false", Theme.RED, false),
                new Act("Whitelist ON", "whitelist on", Theme.TEAL, false),
                new Act("Whitelist OFF", "whitelist off", Theme.TEAL, false),
                new Act("WL reload", "whitelist reload", Theme.TEAL, false),
                new Act("Seed", "seed", Theme.GRAY, false)));

        commandField = addSelectableChild(new TextFieldWidget(textRenderer, left, cy + 114, 252, 20, Text.literal("Command")));
        commandField.setMaxLength(256);
        commandField.setText(commandText);
        commandField.setSuggestion(commandText.isEmpty() ? "Run any command (no /)" : null);
        commandField.setChangedListener(s -> {
            commandText = s;
            commandField.setSuggestion(s.isEmpty() ? "Run any command (no /)" : null);
        });
        addButton(TAB_SERVER, left + 256, cy + 114, 72, 20, "Run", Theme.BLUE, this::runCustom);

        updateTabState();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private FlatButton addButton(int tabIndex, int x, int y, int w, int h, String label, int color, Runnable action) {
        FlatButton b = new FlatButton(x, y, w, h, label, color, action);
        b.tab = tabIndex;
        buttons.add(b);
        return b;
    }

    private void addGrid(int tabIndex, int left, int top, boolean needsTarget, List<Act> acts) {
        for (int i = 0; i < acts.size(); i++) {
            final Act a = acts.get(i);
            int col = i % 4;
            int row = i / 4;
            Runnable run = needsTarget ? () -> runForTarget(a.cmd()) : () -> sendCommand(a.cmd());
            FlatButton b = addButton(tabIndex, left + col * 83, top + row * 22, 79, 20, a.label(), a.color(), run);
            b.confirm = a.confirm();
        }
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> allItems() {
        if (itemCache == null) {
            itemCache = Registries.ITEM.getIds().stream()
                    .map(Identifier::toString)
                    .filter(s -> !s.equals("minecraft:air"))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return itemCache;
    }

    private static String prettyId(String id) {
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }

    private ItemStack iconFor(String id) {
        return iconCache.computeIfAbsent(id, k -> {
            Identifier ident = Identifier.tryParse(k);
            return ident == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(ident));
        });
    }

    private void drawHead(DrawContext ctx, Target t, int x, int y) {
        if (t.entry() != null) {
            PlayerSkinDrawer.draw(ctx, t.entry().getSkinTextures(), x, y, 16);
        } else {
            ctx.fill(x, y, x + 16, y + 16, 0xFF555B66);
            ctx.drawText(textRenderer, "?", x + 5, y + 4, Theme.TEXT, false);
        }
    }

    private void setStatus(String msg, int color) {
        status = msg;
        statusColor = color;
    }

    private void refreshPlayers() {
        String me = client.player.getName().getString();

        TreeMap<String, PlayerListEntry> others = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        PlayerListEntry mine = null;
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (net != null) {
            for (PlayerListEntry entry : net.getPlayerList()) {
                String n = entry.getProfile().name();
                if (n.equalsIgnoreCase(me)) {
                    mine = entry;
                } else {
                    others.put(n, entry);
                }
            }
        }
        if (aimedPlayer != null && !aimedPlayer.equalsIgnoreCase(me) && !others.containsKey(aimedPlayer)) {
            others.put(aimedPlayer, null);
        }

        List<Target> list = new ArrayList<>();
        list.add(new Target(me, "Myself (" + me + ")", mine));
        for (Map.Entry<String, PlayerListEntry> e : others.entrySet()) {
            String n = e.getKey();
            list.add(new Target(n, n.equals(aimedPlayer) ? n + "  (aimed at)" : n, e.getValue()));
        }

        String want = targetName != null ? targetName : aimedPlayer;
        int sel = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equals(want)) sel = i;
        }
        playerDropdown.setOptions(list, sel);
        targetName = list.get(sel).name();
    }

    private void onSearchChanged(String text) {
        searchText = text;
        searchField.setSuggestion(text.isEmpty() ? SEARCH_HINT : null);
        applyFilter();
        itemDropdown.setOpen(!text.isEmpty());
    }

    private void applyFilter() {
        String q = searchText.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        List<String> filtered = allItems().stream()
                .filter(id -> id.contains(q))
                .collect(Collectors.toList());
        int sel = filtered.indexOf(selectedItem);
        if (sel < 0 && !filtered.isEmpty()) sel = 0;
        itemDropdown.setOptions(filtered, sel);
        selectedItem = sel >= 0 ? filtered.get(sel) : null;
    }

    private void onEnchSelected(Enchants.Ench e) {
        selectedEnch = e.id();
        levelText = String.valueOf(e.max());
        if (levelField != null) levelField.setText(levelText);
    }

    private void refreshUnbreakableLabel() {
        unbreakableButton.label = unbreakable ? "Unbreakable: ON" : "Unbreakable: OFF";
        unbreakableButton.color = unbreakable ? Theme.GREEN : Theme.GRAY;
    }

    private void setTab(int t) {
        tab = t;
        for (Dropdown<?> d : dropdowns) d.close();
        updateTabState();
    }

    private void updateTabState() {
        for (int i = 0; i < tabButtons.length; i++) {
            tabButtons[i].selected = (i == tab);
        }

        searchField.visible = (tab == TAB_ITEMS);
        countField.visible = (tab == TAB_ITEMS);
        levelField.visible = (tab == TAB_ITEMS);
        commandField.visible = (tab == TAB_SERVER);
        itemDropdown.visible = (tab == TAB_ITEMS);
        enchDropdown.visible = (tab == TAB_ITEMS);

        unfocusFields();
    }

    private void unfocusFields() {
        searchField.setFocused(false);
        countField.setFocused(false);
        levelField.setFocused(false);
        commandField.setFocused(false);
    }

    private boolean typing() {
        return searchField.isFocused() || countField.isFocused()
                || levelField.isFocused() || commandField.isFocused();
    }

    private boolean shows(FlatButton b) {
        return b.tab < 0 || b.tab == tab;
    }

    // --------------------------------------------------------------- commands

    private void sendCommand(String cmd) {
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (net == null) return;
        net.sendChatCommand(cmd); // no leading slash
        setStatus("Sent: /" + cmd, 0xFF7EE787);
    }

    private void runForTarget(String template) {
        if (targetName == null) {
            setStatus("Pick a target player first.", 0xFFFF7B72);
            return;
        }
        sendCommand(template.replace("%t", targetName));
    }

    private void runCustom() {
        String cmd = commandField.getText().trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) {
            setStatus("Type a command first.", 0xFFFF7B72);
            return;
        }
        sendCommand(cmd);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void addEnchant() {
        Enchants.Ench e = enchDropdown.getSelected();
        if (e == null) return;
        int level = Math.max(1, Math.min(255, parseInt(levelField.getText(), e.max())));
        chosen.put(e.id(), level);
        setStatus("Added " + e.name() + " " + Enchants.roman(level) + ".", 0xFF7EE787);
    }

    private void giveSelected() {
        if (targetName == null) {
            setStatus("Pick a target player first.", 0xFFFF7B72);
            return;
        }
        if (selectedItem == null) {
            setStatus("Pick an item first.", 0xFFFF7B72);
            return;
        }
        int count = Math.max(1, Math.min(9999, parseInt(countField.getText(), 1)));

        List<String> components = new ArrayList<>();
        if (!chosen.isEmpty()) {
            String list = chosen.entrySet().stream()
                    .map(e -> "\"minecraft:" + e.getKey() + "\":" + e.getValue())
                    .collect(Collectors.joining(","));
            components.add("enchantments={" + list + "}");
        }
        if (unbreakable) components.add("unbreakable={}");

        String item = selectedItem;
        if (!components.isEmpty()) item += "[" + String.join(",", components) + "]";

        sendCommand("give " + targetName + " " + item + " " + count);
    }

    // ------------------------------------------------------------------ input

    private List<Chip> chips() {
        List<Chip> out = new ArrayList<>();
        int left = panelLeft + PAD;
        int right = left + CW;
        int baseY = panelTop + CONTENT_Y + 90;
        int cx = left;
        int cyy = baseY;
        for (Map.Entry<String, Integer> e : chosen.entrySet()) {
            int w = textRenderer.getWidth(chipText(e.getKey(), e.getValue())) + 20;
            if (cx + w > right) {
                cx = left;
                cyy += 16;
            }
            if (cyy > baseY + 32) break; // at most 3 rows
            out.add(new Chip(e.getKey(), cx, cyy, w));
            cx += w + 4;
        }
        return out;
    }

    private String chipText(String id, int level) {
        return Enchants.byId(id).name() + " " + Enchants.roman(level);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x();
            double my = click.y();

            // an open dropdown gets first pick (it closes itself on an outside click)
            for (Dropdown<?> d : dropdowns) {
                if (d.isOpen() && d.mouseClicked(mx, my)) {
                    unfocusFields();
                    return true;
                }
            }
            for (Dropdown<?> d : dropdowns) {
                if (!d.isOpen() && d.mouseClicked(mx, my)) {
                    unfocusFields();
                    return true;
                }
            }
            for (FlatButton b : buttons) {
                if (shows(b) && b.click(mx, my)) {
                    unfocusFields();
                    return true;
                }
            }
            if (tab == TAB_ITEMS) {
                for (Chip c : chips()) {
                    if (mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + 14) {
                        chosen.remove(c.id());
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (Dropdown<?> d : dropdowns) {
            if (d.isOpen() && d.mouseScrolled(verticalAmount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.isEscape()) {
            boolean anyOpen = false;
            for (Dropdown<?> d : dropdowns) {
                if (d.isOpen()) {
                    d.close();
                    anyOpen = true;
                }
            }
            if (anyOpen) return true;
        }

        boolean typing = typing();

        if (!typing && OpGuiClient.OPEN_KEY.matchesKey(input)) {
            close();
            return true;
        }
        if (typing && input.isEnter()) {
            if (commandField.isFocused()) runCustom();
            else if (levelField.isFocused()) addEnchant();
            else giveSelected();
            return true;
        }
        return super.keyPressed(input);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // dimmed/blurred world background

        int pl = panelLeft;
        int pt = panelTop;
        int left = pl + PAD;

        // panel
        ctx.fill(pl - 1, pt - 1, pl + PW + 1, pt + PH + 1, Theme.BORDER);
        ctx.fill(pl, pt, pl + PW, pt + PH, Theme.PANEL);
        ctx.fill(pl, pt, pl + PW, pt + 2, Theme.ACCENT);
        ctx.drawText(textRenderer, "OP Menu", left, pt + 7, Theme.TEXT, true);
        String hint = "Aim at a player, then press G";
        ctx.drawText(textRenderer, hint, pl + PW - PAD - textRenderer.getWidth(hint), pt + 7, Theme.MUTED, false);
        ctx.fill(left, pt + 70, pl + PW - PAD, pt + 71, Theme.BORDER);

        if (tab == TAB_PLAYERS) drawTargetCard(ctx, left, pt + CONTENT_Y + 94);
        if (tab == TAB_ITEMS) ctx.drawText(textRenderer, "ENCHANTMENTS", left, pt + CONTENT_Y + 50, Theme.MUTED, false);

        for (FlatButton b : buttons) {
            if (shows(b)) b.render(ctx, textRenderer, mouseX, mouseY);
        }

        if (tab == TAB_ITEMS) drawChips(ctx, mouseX, mouseY);

        searchField.render(ctx, mouseX, mouseY, delta);
        countField.render(ctx, mouseX, mouseY, delta);
        levelField.render(ctx, mouseX, mouseY, delta);
        commandField.render(ctx, mouseX, mouseY, delta);

        for (Dropdown<?> d : dropdowns) d.renderHeader(ctx, mouseX, mouseY);

        if (!status.isEmpty()) {
            ctx.drawText(textRenderer, textRenderer.trimToWidth(status, CW), left, pt + PH - 17, statusColor, false);
        }

        // open lists are drawn last, in a fresh layer so they sit on top of every other widget
        ctx.createNewRootLayer();
        for (Dropdown<?> d : dropdowns) d.renderList(ctx, mouseX, mouseY);
    }

    private void drawTargetCard(DrawContext ctx, int x, int y) {
        int w = CW;
        int h = 38;
        ctx.fill(x, y, x + w, y + h, Theme.BORDER);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, Theme.PANEL_LIGHT);

        Target t = playerDropdown.getSelected();
        if (t == null) return;

        if (t.entry() != null) {
            PlayerSkinDrawer.draw(ctx, t.entry().getSkinTextures(), x + 4, y + 3, 32);
        } else {
            ctx.fill(x + 4, y + 3, x + 36, y + 35, 0xFF555B66);
        }
        ctx.drawText(textRenderer, t.name(), x + 44, y + 9, Theme.TEXT, true);

        String me = client.player.getName().getString();
        String sub;
        if (t.name().equalsIgnoreCase(me)) sub = "That's you. Buttons above apply to this player.";
        else if (t.name().equals(aimedPlayer)) sub = "You were aiming at this player.";
        else sub = "Online player. Buttons above apply to them.";
        ctx.drawText(textRenderer, textRenderer.trimToWidth(sub, w - 52), x + 44, y + 22, Theme.MUTED, false);
    }

    private void drawChips(DrawContext ctx, int mx, int my) {
        if (chosen.isEmpty()) {
            ctx.drawText(textRenderer, "No enchantments added. Pick one, set a level, press Add.",
                    panelLeft + PAD, panelTop + CONTENT_Y + 92, Theme.MUTED, false);
            return;
        }
        for (Chip c : chips()) {
            boolean hover = mx >= c.x() && mx < c.x() + c.w() && my >= c.y() && my < c.y() + 14;
            ctx.fill(c.x(), c.y(), c.x() + c.w(), c.y() + 14, hover ? Theme.RED : Theme.BORDER);
            ctx.fill(c.x() + 1, c.y() + 1, c.x() + c.w() - 1, c.y() + 13, hover ? 0xFF4A2326 : 0xFF2B3340);
            ctx.fill(c.x() + 1, c.y() + 1, c.x() + 3, c.y() + 13, Theme.ACCENT);
            ctx.drawText(textRenderer, chipText(c.id(), chosen.get(c.id())), c.x() + 7, c.y() + 3, Theme.TEXT, false);
            ctx.drawText(textRenderer, "x", c.x() + c.w() - 10, c.y() + 3, hover ? Theme.TEXT : Theme.MUTED, false);
        }
    }
}
