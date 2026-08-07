package com.arxyt.dominionsword.pomkotscompat.client;

import com.arxyt.dominionsword.pomkotscompat.client.renderer.PilebunkerEffectRenderer;
import com.arxyt.dominionsword.pomkotscompat.client.renderer.RisingBlockRenderer;
import com.arxyt.dominionsword.pomkotscompat.registry.PomkotsEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Client-side entity renderer registration. */
public final class PomkotsEntitiesClient {
    private PomkotsEntitiesClient() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PomkotsEntities.PILEBUNKER_EFFECT.get(), PilebunkerEffectRenderer::new);
        event.registerEntityRenderer(PomkotsEntities.RISING_BLOCK.get(), RisingBlockRenderer::new);
    }
}
