package com.arxyt.dominionsword.pomkotscompat.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * A flat expanding ring that reproduces the PMV01 pilebunker shockwave at the impact point.
 * Grows quickly and fades out over roughly half a second.
 */
public class ShockwaveRingParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected ShockwaveRingParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 12;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.quadSize = 0.35F;
        this.alpha = 1.0F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float t = this.age / (float) this.lifetime;
        this.quadSize = 0.35F * (1.0F + 7.0F * t * t);
        this.alpha = Math.max(0.0F, 1.0F - t);
        this.setSpriteFromAge(this.sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new ShockwaveRingParticle(level, x, y, z, this.sprites);
        }
    }
}
