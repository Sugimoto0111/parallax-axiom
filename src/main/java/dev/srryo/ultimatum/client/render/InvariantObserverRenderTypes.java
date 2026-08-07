package dev.srryo.ultimatum.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.srryo.ultimatum.UltimatumMod;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Layered traced-film pass bound to the Observer's supplied mask. */
final class InvariantObserverRenderTypes extends RenderType {
    private static final ResourceLocation MASK = new ResourceLocation(
            UltimatumMod.MOD_ID, "textures/item/invariant_observer.png");

    static final RenderType TRACE = create(
            "ultimatum_invariant_observer_trace",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(
                            InvariantObserverShaders::shader))
                    .setTextureState(new TextureStateShard(MASK, false, false))
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    private InvariantObserverRenderTypes(String name, VertexFormat format,
                                         VertexFormat.Mode mode, int bufferSize,
                                         boolean affectsCrumbling, boolean sortOnUpload,
                                         Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload,
                setupState, clearState);
    }
}
