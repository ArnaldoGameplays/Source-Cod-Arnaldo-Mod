package br.com.arnaldo.ai;

import br.com.arnaldo.ArnaldoEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** An explicit owner attack outranks following, breeding and automatic retaliation. */
public final class PriorityAttackGoal extends MeleeAttackGoal {
    private final ArnaldoEntity pet;
    private int repath;
    public PriorityAttackGoal(ArnaldoEntity pet) { super(pet,1.55,true); this.pet=pet; }
    @Override public boolean canUse() { return !pet.isOrderedToSit() && pet.getPriorityVictim()!=null; }
    @Override public boolean canContinueToUse() { return canUse(); }
    @Override public void start() { super.start(); repath=0; }
    @Override public void tick() {
        var victim=pet.getPriorityVictim();
        if(victim==null) return;
        pet.setTarget(victim);
        if(--repath<=0) { pet.getNavigation().moveTo(victim,1.55); repath=4; }
        super.tick();
    }
}
