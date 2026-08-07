package com.arxyt.dominionsword.pomkotscompat.particle;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Common particle registrations for the Pomkots mech compatibility. */
public final class PomkotsParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, DominionSwordPomkotsCompatMod.MODID);
    public static final RegistryObject<SimpleParticleType> SHOCKWAVE_RING =
            PARTICLES.register("shockwave_ring", () -> new SimpleParticleType(true));

    private PomkotsParticles() {
    }
}
