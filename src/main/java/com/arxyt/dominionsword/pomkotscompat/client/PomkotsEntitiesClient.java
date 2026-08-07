package com.arxyt.dominionsword.pomkotscompat.client;

import com.arxyt.dominionsword.pomkotscompat.client.renderer.RisingBlockRenderer;
import com.arxyt.dominionsword.pomkotscompat.client.renderer.GroundCrackBlockRenderer;
import com.arxyt.dominionsword.pomkotscompat.registry.PomkotsEntities;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** Client-side entity renderer registration. */
public final class PomkotsEntitiesClient {
    private PomkotsEntitiesClient() {
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PomkotsEntities.RISING_BLOCK.get(), RisingBlockRenderer::new);
        event.registerEntityRenderer(PomkotsEntities.GROUND_CRACK_BLOCK.get(), GroundCrackBlockRenderer::new);
    }
}
