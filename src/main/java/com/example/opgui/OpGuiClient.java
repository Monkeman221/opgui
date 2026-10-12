package com.example.opgui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class OpGuiClient implements ClientModInitializer {
    public static KeyBinding OPEN_KEY;

    @Override
    public void onInitializeClient() {
        // Since 1.21.9 key bindings need a Category object instead of a plain string.
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("opgui", "main"));

        OPEN_KEY = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.opgui.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, category));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (OPEN_KEY.wasPressed()) {
                if (client.player == null || client.world == null) continue;
                if (client.currentScreen == null) {
                    client.setScreen(new OpScreen(findAimedPlayer(client)));
                }
            }
        });
    }

    /** Returns the name of the player you're looking at (up to 64 blocks), or null. */
    private static String findAimedPlayer(MinecraftClient client) {
        Entity cam = client.getCameraEntity();
        if (cam == null) return null;

        double range = 64.0;
        Vec3d start = cam.getCameraPosVec(1.0f);
        Vec3d look = cam.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(range));
        Box box = cam.getBoundingBox().stretch(look.multiply(range)).expand(1.0);

        EntityHitResult hit = ProjectileUtil.raycast(
                cam, start, end, box,
                e -> e instanceof PlayerEntity && e != client.player && !e.isSpectator(),
                range * range);

        if (hit != null && hit.getEntity() instanceof PlayerEntity p) {
            return p.getName().getString();
        }
        return null;
    }
}
