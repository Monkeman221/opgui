package com.example.opgui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class OpScreen extends Screen {
    private record Target(String name, String label) {}

    private static List<String> itemCache;
    private static String selectedItem = "minecraft:diamond";

    private static final String SEARCH_HINT = "Search items (e.g. diamond)";

    private final String aimedPlayer; // player you were looking at when you opened the menu (may be null)
    private final List<Dropdown<?>> dropdowns = new ArrayList<>();
    private final List<ButtonWidget> actionButtons = new ArrayList<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    private Dropdown<Target> playerDropdown;
    private Dropdown<String> itemDropdown;
    private TextFieldWidget searchField;
    private TextFieldWidget countField;
    private ButtonWidget actionsTab;
    private ButtonWidget itemsTab;
    private ButtonWidget giveButton;

    // state that survives window resizes
    private int tab = 0;
    private String targetName = null;
    private String searchText = "";
    private String countText = "1";

    private int panelLeft;
    private int panelTop;
    private Text status = Text.empty();
    private int statusColor = 0xFFAAAAAA;

    public OpScreen(String aimedPlayer) {
        super(Text.literal("OP Menu"));
        this.aimedPlayer = aimedPlayer;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void init() {
        dropdowns.clear();
        actionButtons.clear();

        final int panelW = 316;
        panelLeft = (width - panelW) / 2;
        panelTop = Math.max(6, (height - 170) / 4);
        final int left = panelLeft;
        final int top = panelTop;

        // --- target player dropdown (always visible) ---
        playerDropdown = new Dropdown<Target>(textRenderer, left, top, 200, 8,
                Target::label, null, t -> targetName = t.name());
        playerDropdown.setPlaceholder("Target player");
        dropdowns.add(playerDropdown);
        refreshPlayers();

        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh players"), b -> refreshPlayers())
                .dimensions(left + 204, top, 112, 20).build());

        // --- tabs ---
        actionsTab = addDrawableChild(ButtonWidget.builder(Text.literal("Player Actions"), b -> setTab(0))
                .dimensions(left, top + 26, 156, 20).build());
        itemsTab = addDrawableChild(ButtonWidget.builder(Text.literal("Give Items"), b -> setTab(1))
                .dimensions(left + 160, top + 26, 156, 20).build());

        final int cy = top + 52;

        // --- page 1: actions on the selected player ---
        String[][] actions = {
                {"Op", "op %t"},
                {"Deop", "deop %t"},
                {"Kick", "kick %t"},
                {"Clear inv", "clear %t"},
                {"Survival", "gamemode survival %t"},
                {"Creative", "gamemode creative %t"},
                {"Adventure", "gamemode adventure %t"},
                {"Spectator", "gamemode spectator %t"},
                {"Heal", "effect give %t minecraft:instant_health 1 10 true"},
                {"Feed", "effect give %t minecraft:saturation 1 20 true"},
                {"TP to me", "tp %t @s"},
                {"TP to them", "tp @s %t"},
        };
        for (int i = 0; i < actions.length; i++) {
            final String template = actions[i][1];
            int col = i % 4;
            int row = i / 4;
            actionButtons.add(addDrawableChild(ButtonWidget.builder(Text.literal(actions[i][0]), b -> runForTarget(template))
                    .dimensions(left + col * 80, cy + row * 22, 76, 20).build()));
        }

        // world shortcuts (not tied to the target)
        String[][] world = {
                {"Day", "time set day"},
                {"Night", "time set night"},
                {"Sunny", "weather clear"},
                {"Rain", "weather rain"},
        };
        for (int i = 0; i < world.length; i++) {
            final String cmd = world[i][1];
            actionButtons.add(addDrawableChild(ButtonWidget.builder(Text.literal(world[i][0]), b -> sendCommand(cmd))
                    .dimensions(left + i * 80, cy + 3 * 22 + 6, 76, 20).build()));
        }

        // --- page 2: give items ---
        searchField = new TextFieldWidget(textRenderer, left, cy, 254, 20, Text.literal("Search"));
        searchField.setMaxLength(64);
        searchField.setText(searchText);
        searchField.setSuggestion(searchText.isEmpty() ? SEARCH_HINT : null);
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        countField = new TextFieldWidget(textRenderer, left + 258, cy, 58, 20, Text.literal("Amount"));
        countField.setMaxLength(4);
        countField.setTextPredicate(s -> s.matches("\\d*"));
        countField.setText(countText);
        countField.setChangedListener(s -> countText = s);
        addDrawableChild(countField);

        itemDropdown = new Dropdown<String>(textRenderer, left, cy + 24, 254, 5,
                OpScreen::prettyId, this::iconFor, id -> selectedItem = id);
        itemDropdown.setPlaceholder("Pick an item");
        dropdowns.add(itemDropdown);
        applyFilter();

        giveButton = addDrawableChild(ButtonWidget.builder(Text.literal("Give"), b -> giveSelected())
                .dimensions(left + 258, cy + 24, 58, 20).build());

        updateTabState();
    }

    @Override
    public boolean shouldPause() {
        return false;
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

    private void setStatus(String msg, int color) {
        status = Text.literal(msg);
        statusColor = color;
    }

    private void refreshPlayers() {
        String me = client.player.getName().getString();

        TreeSet<String> others = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (net != null) {
            for (PlayerListEntry entry : net.getPlayerList()) {
                String n = entry.getProfile().name();
                if (!n.equalsIgnoreCase(me)) others.add(n);
            }
        }
        if (aimedPlayer != null && !aimedPlayer.equalsIgnoreCase(me)) others.add(aimedPlayer);

        List<Target> list = new ArrayList<>();
        list.add(new Target(me, "Myself (" + me + ")"));
        for (String n : others) {
            list.add(new Target(n, n.equals(aimedPlayer) ? n + "  (aimed at)" : n));
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

    private void setTab(int t) {
        tab = t;
        for (Dropdown<?> d : dropdowns) d.close();
        updateTabState();
    }

    private void updateTabState() {
        actionsTab.active = tab != 0;
        itemsTab.active = tab != 1;

        for (ButtonWidget b : actionButtons) b.visible = (tab == 0);

        searchField.visible = (tab == 1);
        countField.visible = (tab == 1);
        giveButton.visible = (tab == 1);
        itemDropdown.visible = (tab == 1);

        if (tab != 1) {
            searchField.setFocused(false);
            countField.setFocused(false);
        }
    }

    // --------------------------------------------------------------- commands

    private void sendCommand(String cmd) {
        ClientPlayNetworkHandler net = client.getNetworkHandler();
        if (net == null) return;
        net.sendChatCommand(cmd); // no leading slash
        setStatus("Sent: /" + cmd, 0xFF88FF88);
    }

    private void runForTarget(String template) {
        if (targetName == null) {
            setStatus("Pick a target player first.", 0xFFFF7777);
            return;
        }
        sendCommand(template.replace("%t", targetName));
    }

    private void giveSelected() {
        if (targetName == null) {
            setStatus("Pick a target player first.", 0xFFFF7777);
            return;
        }
        if (selectedItem == null) {
            setStatus("Pick an item first.", 0xFFFF7777);
            return;
        }
        int count = 1;
        try {
            count = Integer.parseInt(countField.getText().trim());
        } catch (NumberFormatException ignored) {
        }
        count = Math.max(1, Math.min(count, 9999));
        sendCommand("give " + targetName + " " + selectedItem + " " + count);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x();
            double my = click.y();
            // an open dropdown gets first pick (it closes itself on an outside click)
            for (Dropdown<?> d : dropdowns) {
                if (d.isOpen() && d.mouseClicked(mx, my)) return true;
            }
            for (Dropdown<?> d : dropdowns) {
                if (!d.isOpen() && d.mouseClicked(mx, my)) return true;
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

        boolean typing = searchField.isFocused() || countField.isFocused();

        if (!typing && OpGuiClient.OPEN_KEY.matchesKey(input)) {
            close();
            return true;
        }
        if (typing && input.isEnter()) {
            giveSelected();
            return true;
        }
        return super.keyPressed(input);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        for (Dropdown<?> d : dropdowns) d.renderHeader(ctx, mouseX, mouseY);

        ctx.drawText(textRenderer, status, panelLeft, panelTop + 152, statusColor, false);

        // open lists are drawn last, in a fresh layer so they sit on top of every other widget
        ctx.createNewRootLayer();
        for (Dropdown<?> d : dropdowns) d.renderList(ctx, mouseX, mouseY);
    }
}
