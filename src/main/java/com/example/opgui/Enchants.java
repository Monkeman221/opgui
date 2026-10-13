package com.example.opgui;

import java.util.List;

/** Vanilla enchantments and their normal maximum levels. */
public final class Enchants {
    private Enchants() {}

    public record Ench(String id, String name, int max) {}

    public static final List<Ench> ALL = List.of(
            new Ench("aqua_affinity", "Aqua Affinity", 1),
            new Ench("bane_of_arthropods", "Bane of Arthropods", 5),
            new Ench("binding_curse", "Curse of Binding", 1),
            new Ench("blast_protection", "Blast Protection", 4),
            new Ench("breach", "Breach", 4),
            new Ench("channeling", "Channeling", 1),
            new Ench("density", "Density", 5),
            new Ench("depth_strider", "Depth Strider", 3),
            new Ench("efficiency", "Efficiency", 5),
            new Ench("feather_falling", "Feather Falling", 4),
            new Ench("fire_aspect", "Fire Aspect", 2),
            new Ench("fire_protection", "Fire Protection", 4),
            new Ench("flame", "Flame", 1),
            new Ench("fortune", "Fortune", 3),
            new Ench("frost_walker", "Frost Walker", 2),
            new Ench("impaling", "Impaling", 5),
            new Ench("infinity", "Infinity", 1),
            new Ench("knockback", "Knockback", 2),
            new Ench("looting", "Looting", 3),
            new Ench("loyalty", "Loyalty", 3),
            new Ench("luck_of_the_sea", "Luck of the Sea", 3),
            new Ench("lure", "Lure", 3),
            new Ench("mending", "Mending", 1),
            new Ench("multishot", "Multishot", 1),
            new Ench("piercing", "Piercing", 4),
            new Ench("power", "Power", 5),
            new Ench("projectile_protection", "Projectile Protection", 4),
            new Ench("protection", "Protection", 4),
            new Ench("punch", "Punch", 2),
            new Ench("quick_charge", "Quick Charge", 3),
            new Ench("respiration", "Respiration", 3),
            new Ench("riptide", "Riptide", 3),
            new Ench("sharpness", "Sharpness", 5),
            new Ench("silk_touch", "Silk Touch", 1),
            new Ench("smite", "Smite", 5),
            new Ench("soul_speed", "Soul Speed", 3),
            new Ench("sweeping_edge", "Sweeping Edge", 3),
            new Ench("swift_sneak", "Swift Sneak", 3),
            new Ench("thorns", "Thorns", 3),
            new Ench("unbreaking", "Unbreaking", 3),
            new Ench("vanishing_curse", "Curse of Vanishing", 1),
            new Ench("wind_burst", "Wind Burst", 3)
    );

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) {
            if (ALL.get(i).id().equals(id)) return i;
        }
        return 0;
    }

    public static Ench byId(String id) {
        return ALL.get(indexOf(id));
    }

    public static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return (n >= 1 && n <= 10) ? r[n] : String.valueOf(n);
    }
}
