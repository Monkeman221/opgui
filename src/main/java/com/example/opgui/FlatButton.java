package com.example.opgui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** A simple self-drawn button so the whole menu shares one look. */
public class FlatButton {
    public int x, y, w, h;
    public String label;
    public int color;
    public int tab = -1;          // which tab shows this button (-1 = every tab)
    public boolean active = true; // clickable
    public boolean selected = false; // accent underline (used for the tab buttons)
    public boolean confirm = false;  // needs a second click within 2.5s

    private final Runnable action;
    private long armedUntil = 0;

    public FlatButton(int x, int y, int w, int h, String label, int color, Runnable action) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.label = label;
        this.color = color;
        this.action = action;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public void render(DrawContext ctx, TextRenderer tr, int mx, int my) {
        boolean armed = confirm && System.currentTimeMillis() < armedUntil;
        boolean hover = active && contains(mx, my);

        int base = color;
        if (armed) base = Theme.RED;
        if (!active) base = Theme.darken(base, 0.45f);
        else if (hover) base = Theme.lighten(base, 0.18f);

        ctx.fill(x, y, x + w, y + h, Theme.darken(color, 0.55f));
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, base);
        ctx.fill(x + 1, y + 1, x + w - 1, y + 2, Theme.lighten(base, 0.12f));
        if (selected) {
            ctx.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, Theme.ACCENT);
        }

        String text = armed ? "Sure?" : label;
        int tx = x + (w - tr.getWidth(text)) / 2;
        int ty = y + (h - 8) / 2 + 1;
        ctx.drawText(tr, text, tx, ty, active ? Theme.TEXT : Theme.MUTED, false);
    }

    /** Returns true if the click landed on this button (and ran it, unless it was only "armed"). */
    public boolean click(double mx, double my) {
        if (!active || !contains(mx, my)) return false;
        if (confirm) {
            long now = System.currentTimeMillis();
            if (now >= armedUntil) {
                armedUntil = now + 2500;
                return true;
            }
            armedUntil = 0;
        }
        action.run();
        return true;
    }
}
