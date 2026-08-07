package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/** Owns the view-dependent interior shader and its reload-safe uniforms. */
@Mod.EventBusSubscriber(modid = UltimatumMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FinalConclusionShaders {
    private static final float DEG_TO_RAD = (float) Math.PI / 180.0F;

    private static ShaderInstance interiorShader;
    private static Uniform timeUniform;
    private static Uniform attackUniform;
    private static Uniform cameraYawUniform;
    private static Uniform cameraPitchUniform;
    private static Uniform patternScaleUniform;

    private FinalConclusionShaders() {
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        ShaderInstance shader = new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation(UltimatumMod.MOD_ID, "final_conclusion_interior"),
                DefaultVertexFormat.NEW_ENTITY);
        event.registerShader(shader, FinalConclusionShaders::acceptShader);
    }

    private static void acceptShader(ShaderInstance shader) {
        interiorShader = shader;
        timeUniform = shader.getUniform("Time");
        attackUniform = shader.getUniform("Attack");
        cameraYawUniform = shader.getUniform("CameraYaw");
        cameraPitchUniform = shader.getUniform("CameraPitch");
        patternScaleUniform = shader.getUniform("PatternScale");
    }

    static ShaderInstance interiorShader() {
        ShaderInstance shader = interiorShader;
        return shader != null ? shader : GameRenderer.getRendertypeEntityTranslucentShader();
    }

    static void prepare(float time, float attack, ItemDisplayContext context) {
        if (interiorShader == null) {
            return;
        }
        set(timeUniform, time);
        set(attackUniform, attack);
        set(cameraYawUniform, Minecraft.getInstance().gameRenderer.getMainCamera().getYRot()
                * DEG_TO_RAD);
        set(cameraPitchUniform, Minecraft.getInstance().gameRenderer.getMainCamera().getXRot()
                * DEG_TO_RAD);
        set(patternScaleUniform, patternScale(context));
    }

    private static float patternScale(ItemDisplayContext context) {
        return switch (context) {
            case GUI -> 0.82F;
            case GROUND, FIXED -> 1.12F;
            default -> 1.0F;
        };
    }

    private static void set(Uniform uniform, float value) {
        if (uniform != null) {
            uniform.set(value);
        }
    }
}
