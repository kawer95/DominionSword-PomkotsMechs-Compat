package com.arxyt.dominionsword.pomkotscompat.registry;

import com.arxyt.dominionsword.pomkotscompat.DominionSwordPomkotsCompatMod;
import com.arxyt.dominionsword.pomkotscompat.entity.PilebunkerEffectEntity;
import com.arxyt.dominionsword.pomkotscompat.entity.RisingBlockEntity;
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

    public static final RegistryObject<EntityType<RisingBlockEntity>> RISING_BLOCK =
            ENTITY_TYPES.register("rising_block",
                    () -> EntityType.Builder.<RisingBlockEntity>of(RisingBlockEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .build("rising_block"));

    private PomkotsEntities() {
    }
}
