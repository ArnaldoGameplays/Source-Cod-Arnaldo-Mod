package br.com.arnaldo.ai;

import br.com.arnaldo.ArnaldoEntity;
import java.util.Comparator;
import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;

/** Collect nearby usable items while the owner is idle, without interrupting combat or marching. */
public final class CollectEquipmentGoal extends Goal {
    private final ArnaldoEntity pet;
    private ItemEntity item;
    private int repath;
    public CollectEquipmentGoal(ArnaldoEntity pet) { this.pet=pet; setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK)); }
    private boolean available() { return !pet.isOrderedToSit() && !pet.hasAttackOrder() && !pet.isOwnerMoving() && !pet.isInLove(); }
    @Override public boolean canUse() {
        if(!available() || !(pet.level() instanceof ServerLevel level)) return false;
        item=level.getEntitiesOfClass(ItemEntity.class,pet.getBoundingBox().inflate(6,2,6),
            candidate->candidate.isAlive() && !candidate.hasPickUpDelay() && pet.wantsToPickUp(level,candidate.getItem()))
            .stream().min(Comparator.comparingDouble(pet::distanceToSqr)).orElse(null);
        return item!=null;
    }
    @Override public boolean canContinueToUse() {
        return available() && item!=null && item.isAlive() && pet.distanceToSqr(item)<=64
            && pet.wantsToPickUp((ServerLevel)pet.level(),item.getItem());
    }
    @Override public void start() { repath=0; }
    @Override public void tick() {
        pet.getLookControl().setLookAt(item,30,30);
        if(--repath<=0) { pet.getNavigation().moveTo(item,1.0); repath=8; }
    }
    @Override public void stop() { pet.getNavigation().stop(); item=null; }
}
