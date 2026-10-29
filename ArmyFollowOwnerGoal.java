package br.com.arnaldo.ai;

import br.com.arnaldo.ArnaldoEntity;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/** Responsive escort with a trailing slot, personal space, and safe stuck recovery. */
public final class ArmyFollowOwnerGoal extends Goal {
    private final ArnaldoEntity pet;
    private int repathIn;
    private int stuckTicks;
    private float oldWaterCost;
    private Vec3 lastProgress;
    private Vec3 lastDestination;

    public ArmyFollowOwnerGoal(ArnaldoEntity pet) {
        this.pet=pet;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }
    private LivingEntity owner() { return pet.getOwner(); }
    private boolean eligible() {
        var owner=owner();
        return owner!=null && owner.isAlive() && owner.level()==pet.level() && !pet.unableToMoveToOwner() && !pet.hasAttackOrder();
    }
    private boolean closeCombat() {
        var target=pet.getTarget(); var owner=owner();
        return owner!=null && target!=null && target.isAlive()
            && pet.distanceToSqr(owner)<16 && target.distanceToSqr(owner)<36;
    }
    @Override public boolean canUse() {
        if(pet.isInLove() && !pet.isOwnerMoving()) return false;
        return eligible() && !closeCombat() &&
            (pet.isOwnerMoving() || pet.distanceToSqr(owner())>16);
    }
    @Override public boolean canContinueToUse() {
        if(pet.isInLove() && !pet.isOwnerMoving()) return false;
        return eligible() && !closeCombat() &&
            (pet.isOwnerMoving() || pet.distanceToSqr(owner())>6.25);
    }
    @Override public boolean requiresUpdateEveryTick() { return true; }
    @Override public void start() {
        repathIn=0; stuckTicks=0; lastProgress=pet.position(); lastDestination=null;
        oldWaterCost=pet.getPathfindingMalus(PathType.WATER);
        pet.setPathfindingMalus(PathType.WATER,0);
        pet.getNavigation().stop();
    }
    @Override public void stop() {
        pet.getNavigation().stop();
        pet.setPathfindingMalus(PathType.WATER,oldWaterCost);
    }
    /** Stable trailing slots keep a crowd from all aiming at the player's feet. */
    public Vec3 destination(LivingEntity owner) {
        Vec3 heading=pet.ownerTravelDirection();
        int slot=pet.getUUID().hashCode() & 0x7fffffff;
        double lateral=((slot%7)-3)*0.65;
        double behind=2.8+((slot/7)%3)*0.5;
        return owner.position().subtract(heading.scale(behind))
            .add(-heading.z*lateral,0,heading.x*lateral);
    }
    @Override public void tick() {
        var owner=owner();
        if(owner==null) return;
        pet.getLookControl().setLookAt(owner,30,pet.getMaxHeadXRot());
        double distance=pet.distanceToSqr(owner);
        if(distance>=144) {
            pet.tryToTeleportToOwner();
            repathIn=0; stuckTicks=0; lastProgress=pet.position();
            return;
        }
        // Stop outside the owner's personal space.
        if((!pet.isOwnerMoving() && distance<=9) || distance<4.0) {
            pet.getNavigation().stop(); repathIn=0; stuckTicks=0;
            lastProgress=pet.position();
            return;
        }
        if(--repathIn>0) return;
        repathIn=4;
        Vec3 desired=destination(owner);
        if(pet.position().distanceToSqr(desired)<0.49) {
            pet.getNavigation().stop(); stuckTicks=0; lastProgress=pet.position();
            return;
        }
        double speed=distance>36 ? 1.65 : owner.isSprinting() ? 1.5 : 1.25;
        if(lastDestination==null || desired.distanceToSqr(lastDestination)>0.16 || pet.getNavigation().isDone()) {
            // Use the formation slot when reachable, otherwise approach the owner until in range.
            var path=pet.getNavigation().createPath(desired.x,desired.y,desired.z,0);
            if(path!=null && path.canReach()) pet.getNavigation().moveTo(path,speed);
            else pet.getNavigation().moveTo(owner,speed);
            lastDestination=desired;
        } else pet.getNavigation().setSpeedModifier(speed);
        if(lastProgress!=null && pet.position().distanceToSqr(lastProgress)<0.01) stuckTicks+=4;
        else stuckTicks=0;
        lastProgress=pet.position();
        if(stuckTicks>=40 && distance>36) {
            pet.tryToTeleportToOwner();
            stuckTicks=0; lastDestination=null;
        }
    }
}
