package br.com.arnaldo.neoforge;
import br.com.arnaldo.ArnaldoContent;
import br.com.arnaldo.client.ArnaldoRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
@EventBusSubscriber(modid = ArnaldoContent.MOD_ID, value = Dist.CLIENT)
public final class ArnaldoNeoForgeClient {
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ArnaldoContent.ARNALDO, ArnaldoRenderer::new);
    }
}
