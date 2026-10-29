package br.com.arnaldo.neoforge;
import br.com.arnaldo.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.core.registries.Registries;
@Mod(ArnaldoContent.MOD_ID)
public final class ArnaldoNeoForge {
    public ArnaldoNeoForge(IEventBus bus) {
        bus.addListener((RegisterEvent event) -> {
            event.register(Registries.ENTITY_TYPE, helper -> {
                ArnaldoContent.ARNALDO = ArnaldoContent.createEntityType();
                helper.register(ArnaldoContent.ENTITY_KEY, ArnaldoContent.ARNALDO);
            });
            event.register(Registries.ITEM, helper -> {
                ArnaldoContent.GOLDEN_BERRIES = ArnaldoContent.createBerries();
                helper.register(ArnaldoContent.BERRIES_KEY, ArnaldoContent.GOLDEN_BERRIES);
            });
        });
        bus.addListener((EntityAttributeCreationEvent event) -> event.put(ArnaldoContent.ARNALDO, ArnaldoEntity.createAttributes().build()));
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ArnaldoCommands.register(event.getDispatcher()));
    }
}
