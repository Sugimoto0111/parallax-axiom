package dev.srryo.parallaxaxiom.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ClientKeys {
    public static final KeyMapping CYCLE_REACH = new KeyMapping(
            "key.parallax_axiom.cycle_reach",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.parallax_axiom");
    public static final KeyMapping CYCLE_FLIGHT = new KeyMapping(
            "key.parallax_axiom.cycle_flight",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.parallax_axiom");
    public static final KeyMapping TOGGLE_STEP_ASSIST = new KeyMapping(
            "key.parallax_axiom.toggle_step_assist",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.parallax_axiom");
    public static final KeyMapping TOGGLE_INERTIA = new KeyMapping(
            "key.parallax_axiom.toggle_inertia",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.parallax_axiom");
    public static final KeyMapping TOGGLE_NIGHT_VISION = new KeyMapping(
            "key.parallax_axiom.toggle_night_vision",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.parallax_axiom");
    public static final KeyMapping TOGGLE_ITEM_MAGNET = new KeyMapping(
            "key.parallax_axiom.toggle_item_magnet",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.parallax_axiom");

    private ClientKeys() {
    }
}
