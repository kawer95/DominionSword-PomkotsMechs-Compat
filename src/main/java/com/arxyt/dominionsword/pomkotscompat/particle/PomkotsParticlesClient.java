package com.arxyt.dominionsword.pomkotscompat.particle;

import net.minecraftforge.client.event.RegisterParticleProvidersEvent;

/** Client-side particle provider registration. */
public final class PomkotsParticlesClient {
    private PomkotsParticlesClient() {
    }

    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(PomkotsParticles.SHOCKWAVE_RING.get(), ShockwaveRingParticle.Provider::new);
    }
}
