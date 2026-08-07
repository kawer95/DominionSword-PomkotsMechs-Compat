package com.arxyt.dominionsword.pomkotscompat.registry;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.entity.PilebunkerEffectEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Common entity registrations for the Pomkots mech compatibility. */
public final class PomkotsEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DominionSwordPomkotsCompatMod.MODID);

    public static final RegistryObject<EntityType<PilebunkerEffectEntity>> PILEBUNKER_EFFECT =
            ENTITY_TYPES.register("pilebunker_effect",
                    () -> EntityType.Builder.<PilebunkerEffectEntity>of(PilebunkerEffectEntity::new, MobCategory.MISC)
                            .sized(0.1F, 0.1F)
                            .build("pilebunker_effect"));

    private PomkotsEntities() {
    }
}
