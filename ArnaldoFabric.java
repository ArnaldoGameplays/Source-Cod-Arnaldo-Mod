package br.com.arnaldo.fabric;
import br.com.arnaldo.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
public final class ArnaldoFabric implements ModInitializer {
    @Override public void onInitialize() {
        ArnaldoContent.ARNALDO = Registry.register(BuiltInRegistries.ENTITY_TYPE, ArnaldoContent.ENTITY_KEY, ArnaldoContent.createEntityType());
        ArnaldoContent.GOLDEN_BERRIES = Registry.register(BuiltInRegistries.ITEM, ArnaldoContent.BERRIES_KEY, ArnaldoContent.createBerries());
        FabricDefaultAttributeRegistry.register(ArnaldoContent.ARNALDO, ArnaldoEntity.createAttributes());
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> ArnaldoCommands.register(dispatcher));
    }
}
