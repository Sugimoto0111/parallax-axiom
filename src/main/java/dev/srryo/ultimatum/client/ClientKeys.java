package dev.srryo.ultimatum.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
    public static final KeyMapping CYCLE_REACH = new KeyMapping(
            "key.ultimatum.cycle_reach",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.ultimatum");
    public static final KeyMapping CYCLE_FLIGHT = new KeyMapping(
            "key.ultimatum.cycle_flight",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.ultimatum");
    public static final KeyMapping TOGGLE_STEP_ASSIST = new KeyMapping(
            "key.ultimatum.toggle_step_assist",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.ultimatum");
    public static final KeyMapping TOGGLE_INERTIA = new KeyMapping(
            "key.ultimatum.toggle_inertia",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.ultimatum");
    public static final KeyMapping TOGGLE_NIGHT_VISION = new KeyMapping(
            "key.ultimatum.toggle_night_vision",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.ultimatum");
    public static final KeyMapping TOGGLE_ITEM_MAGNET = new KeyMapping(
            "key.ultimatum.toggle_item_magnet",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.ultimatum");

    private ClientKeys() {
    }
}
