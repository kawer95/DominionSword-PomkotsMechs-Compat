package com.arxyt.dominionsword.pomkotscompat;

import com.arxyt.dominionsword.api.DominionVehicleAdapters;
import com.arxyt.dominionsword.api.DominionSkills;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(DominionSwordPomkotsCompatMod.MODID)
public final class DominionSwordPomkotsCompatMod {
    public static final String MODID = "dominionsword_pomkotsmechs_compat";
    public static final Logger LOGGER = LogUtils.getLogger();
    private final PomkotsMechVehicleAdapter adapter = new PomkotsMechVehicleAdapter();

    public DominionSwordPomkotsCompatMod() {
        DominionVehicleAdapters.register(adapter);
        DominionSkills.register(adapter);
        MinecraftForge.EVENT_BUS.addListener(this::onServerTick);
        LOGGER.info("[DominionSword Pomkots Compat] Pomkots mech skill and vehicle bridge enabled");
    }

    private void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) adapter.tick(event.getServer());
    }
}
