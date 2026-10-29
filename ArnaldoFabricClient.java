package br.com.arnaldo.fabric;
import br.com.arnaldo.ArnaldoContent;
import br.com.arnaldo.client.ArnaldoRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
public final class ArnaldoFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient() { EntityRendererRegistry.register(ArnaldoContent.ARNALDO, ArnaldoRenderer::new); }
}
