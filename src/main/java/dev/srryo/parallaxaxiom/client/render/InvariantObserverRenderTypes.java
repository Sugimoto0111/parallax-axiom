package dev.srryo.parallaxaxiom.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.srryo.parallaxaxiom.ParallaxAxiomMod;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Final Conclusion's traced-film pass bound to the Observer's supplied mask. */
final class InvariantObserverRenderTypes extends RenderType {
    private static final ResourceLocation MASK = new ResourceLocation(
            ParallaxAxiomMod.MOD_ID, "textures/item/invariant_observer.png");

    static final RenderType TRACE = create(
            "parallax_axiom_invariant_observer_trace",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(new ShaderStateShard(
                            FinalConclusionShaders::traceShader))
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
