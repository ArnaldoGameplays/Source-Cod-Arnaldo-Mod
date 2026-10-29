package br.com.arnaldo;
import net.minecraft.world.InteractionHand;
final class AttackAnimation {
    static void swing(ArnaldoEntity entity) { entity.swingForAttack(InteractionHand.MAIN_HAND); }
}
