package com.example.opgui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** A small self-drawn dropdown menu (header + scrollable list that opens above everything else). */
public class Dropdown<T> {
    private static final int HEADER_H = 20;

    private final TextRenderer tr;
    private final Function<T, String> label;
    private final Function<T, ItemStack> icon; // may be null
    private final Consumer<T> onSelect;
    private final int maxRows;
    private final int rowH;

    public int x, y, w;
    public boolean visible = true;

    private boolean open = false;
    private List<T> options = List.of();
    private int selected = -1;
    private int scroll = 0;
    private String placeholder = "Select...";

    public Dropdown(TextRenderer tr, int x, int y, int w, int maxRows,
                    Function<T, String> label, Function<T, ItemStack> icon, Consumer<T> onSelect) {
        this.tr = tr;
        this.x = x;
        this.y = y;
        this.w = w;
        this.maxRows = maxRows;
        this.label = label;
        this.icon = icon;
        this.onSelect = onSelect;
        this.rowH = icon != null ? 18 : 14;
    }

    public void setPlaceholder(String s) {
        this.placeholder = s;
    }

    public void setOptions(List<T> opts, int selectedIndex) {
        this.options = opts;
        this.selected = (selectedIndex >= 0 && selectedIndex < opts.size()) ? selectedIndex : -1;
        this.scroll = 0;
        centerOnSelected();
    }

    public T getSelected() {
        return (selected >= 0 && selected < options.size()) ? options.get(selected) : null;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean value) {
        this.open = value && visible;
        if (this.open) centerOnSelected();
    }

    public void close() {
        this.open = false;
    }

    // ---- geometry ----

    private int rows() {
        return Math.min(maxRows, options.size());
    }

    private int listHeight() {
        return Math.max(1, rows()) * rowH + 2;
    }

    private int listTop() {
        int screenH = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int below = y + HEADER_H;
        int lh = listHeight();
        // open downward if it fits (or if it wouldn't fit upward either); otherwise open upward
        if (below + lh <= screenH || y - lh < 0) return below;
        return y - lh;
    }

    private boolean inHeader(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + HEADER_H;
    }

    private boolean inList(double mx, double my) {
        int lt = listTop();
        return mx >= x && mx < x + w && my >= lt && my < lt + listHeight();
    }

    private void centerOnSelected() {
        int max = Math.max(0, options.size() - maxRows);
        if (selected >= 0) {
            scroll = selected - maxRows / 2;
        }
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ---- input ----

    /** Returns true if the click was consumed. A click outside an open dropdown closes it and is not consumed. */
    public boolean mouseClicked(double mx, double my) {
        if (!visible) return false;

        if (open) {
            if (inHeader(mx, my)) {
                open = false;
                return true;
            }
            if (inList(mx, my)) {
                int row = (int) ((my - listTop() - 1) / rowH);
                int idx = scroll + row;
                if (row >= 0 && row < rows() && idx < options.size()) {
                    selected = idx;
                    open = false;
                    onSelect.accept(options.get(idx));
                }
                return true;
            }
            open = false;
            return false;
        }

        if (inHeader(mx, my)) {
            open = true;
            centerOnSelected();
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double amount) {
        if (!open) return false;
        int max = Math.max(0, options.size() - maxRows);
        scroll -= (int) Math.signum(amount) * 2;
        scroll = Math.max(0, Math.min(scroll, max));
        return true;
    }

    // ---- drawing ----

    public void renderHeader(DrawContext ctx, int mx, int my) {
        if (!visible) return;

        boolean hover = inHeader(mx, my);
        ctx.fill(x, y, x + w, y + HEADER_H, 0xFFA0A0A0);
        ctx.fill(x + 1, y + 1, x + w - 1, y + HEADER_H - 1, (hover || open) ? 0xFF3C3C3C : 0xFF1E1E1E);

        T sel = getSelected();
        String text = sel != null ? label.apply(sel) : placeholder;
        int tx = x + 6;
        if (icon != null && sel != null) {
            ctx.drawItem(icon.apply(sel), x + 3, y + 2);
            tx = x + 24;
        }
        ctx.drawText(tr, tr.trimToWidth(text, w - (tx - x) - 14), tx, y + 6, 0xFFFFFFFF, false);
        ctx.drawText(tr, open ? "^" : "v", x + w - 11, y + 6, 0xFFAAAAAA, false);
    }

    /** Call this last (inside a raised z-offset) so the list draws on top of other widgets. */
    public void renderList(DrawContext ctx, int mx, int my) {
        if (!visible || !open) return;

        int lt = listTop();
        int lh = listHeight();
        ctx.fill(x, lt, x + w, lt + lh, 0xFFA0A0A0);
        ctx.fill(x + 1, lt + 1, x + w - 1, lt + lh - 1, 0xFF101010);

        if (options.isEmpty()) {
            ctx.drawText(tr, "No matches", x + 6, lt + 4, 0xFF888888, false);
            return;
        }

        int n = rows();
        boolean bar = options.size() > maxRows;

        for (int i = 0; i < n; i++) {
            int idx = scroll + i;
            T opt = options.get(idx);
            int ry = lt + 1 + i * rowH;

            if (idx == selected) {
                ctx.fill(x + 1, ry, x + w - 1, ry + rowH, 0xFF2D4A2D);
            }
            boolean hov = mx >= x + 1 && mx < x + w - 1 && my >= ry && my < ry + rowH;
            if (hov) {
                ctx.fill(x + 1, ry, x + w - 1, ry + rowH, 0xFF44446A);
            }

            int tx = x + 6;
            if (icon != null) {
                ctx.drawItem(icon.apply(opt), x + 3, ry + 1);
                tx = x + 24;
            }
            int ty = ry + (rowH - 8) / 2;
            ctx.drawText(tr, tr.trimToWidth(label.apply(opt), x + w - tx - (bar ? 10 : 4)), tx, ty, 0xFFFFFFFF, false);
        }

        if (bar) {
            int trackH = n * rowH;
            int thumbH = Math.max(8, trackH * n / options.size());
            int maxScroll = options.size() - n;
            int thumbY = lt + 1 + (trackH - thumbH) * scroll / maxScroll;
            ctx.fill(x + w - 5, thumbY, x + w - 2, thumbY + thumbH, 0xFF8A8A8A);
        }
    }
}
