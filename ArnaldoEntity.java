package br.com.arnaldo;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import br.com.arnaldo.ai.ArmyFollowOwnerGoal;
import br.com.arnaldo.ai.PriorityAttackGoal;
import br.com.arnaldo.ai.CollectEquipmentGoal;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.gamerules.GameRules;
import java.util.UUID;

/** A pet's ownership/navigation, with human dimensions, health and interactions. */
public class ArnaldoEntity extends TamableAnimal {
    private Vec3 previousOwnerPosition;
    private UUID observedOwner;
    private Vec3 travelDirection=new Vec3(0,0,1);
    private Vec3 lastSafePosition;
    private int movingUntil;
    private EntityReference<LivingEntity> priorityVictim;
    private LivingEntity resolvedVictim;
    private UUID attackOwner;
    private int lastOwnerAttackStamp=Integer.MIN_VALUE;
    private long armyGeneration=-1;
    private boolean godmode;
    public ArnaldoEntity(EntityType<? extends ArnaldoEntity> type, Level level) {
        super(type, level);
        setPathfindingMalus(PathType.POWDER_SNOW, -1.0F);
        setPersistenceRequired();
        applyIdentity();
        setCustomNameVisible(true);
        setHealth(getMaxHealth());
        enableEquipmentPickup();
    }
    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20.0)
            .add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 4.0)
            .add(Attributes.FOLLOW_RANGE, 32.0);
    }
    public static Component playerName() {
        return Component.literal("ArnaldoGameplay").withStyle(ChatFormatting.WHITE);
    }
    private void applyIdentity() { setCustomName(playerName()); }
    public void refreshNicknamePreference() {
        if(level() instanceof ServerLevel server && getOwnerReference()!=null) {
            var preferences=ArnaldoPreferences.get(server);
            setCustomNameVisible(preferences.nickVisible(getOwnerReference().getUUID()));
            godmode=preferences.godmode(getOwnerReference().getUUID());
            double max=preferences.maxHealth(getOwnerReference().getUUID());
            var attribute=getAttribute(Attributes.MAX_HEALTH);
            if(attribute!=null && attribute.getBaseValue()!=max) {
                attribute.setBaseValue(max);
                if(getHealth()>getMaxHealth()) setHealth(getMaxHealth());
            }
            if(armyGeneration<0) armyGeneration=preferences.armyGeneration(getOwnerReference().getUUID());
        }
    }
    public boolean isGodmode() { return godmode; }
    public boolean isArmyRetired() {
        if(!(level() instanceof ServerLevel server) || getOwnerReference()==null) return false;
        refreshNicknamePreference();
        return armyGeneration<ArnaldoPreferences.get(server).armyGeneration(getOwnerReference().getUUID());
    }
    public void removeByOwnerCommand() {
        if(isRemoved()) return;
        if(level() instanceof ServerLevel server) for(var slot:EquipmentSlot.VALUES) {
            var stack=getItemBySlot(slot);
            if(!stack.isEmpty()) { spawnAtLocation(server,stack.copy()); setItemSlot(slot,ItemStack.EMPTY); }
        }
        discard();
    }
    @Override public Component getDisplayName() { return playerName(); }
    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // Migrate already-spawned companions without changing their registry ID or owner.
        applyIdentity();
        priorityVictim=EntityReference.read(input,"ArnaldoPriorityVictim");
        resolvedVictim=null;
        armyGeneration=input.getLongOr("ArnaldoArmyGeneration",0);
        enableEquipmentPickup();
        refreshNicknamePreference();
    }
    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        EntityReference.store(priorityVictim,output,"ArnaldoPriorityVictim");
        output.putLong("ArnaldoArmyGeneration",Math.max(0,armyGeneration));
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new PriorityAttackGoal(this));
        goalSelector.addGoal(1, new StayGoal());
        goalSelector.addGoal(2, new ArmyFollowOwnerGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(4, new CollectEquipmentGoal(this));
        goalSelector.addGoal(5, new BreedGoal(this, 1.0));
        goalSelector.addGoal(6, new TemptGoal(this, 1.0, this::isFood, false));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override public boolean canUse() { return canWanderAroundOwner() && super.canUse(); }
            @Override public boolean canContinueToUse() { return canWanderAroundOwner() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
    }
    public boolean isOwnerMoving() { return tickCount<movingUntil; }
    public Vec3 ownerTravelDirection() { return travelDirection; }
    public boolean canWanderAroundOwner() {
        var owner=getOwner();
        return owner!=null && owner.level()==level() && !isOrderedToSit()
            && !isOwnerMoving() && !hasAttackOrder() && distanceToSqr(owner)<=16;
    }
    private void sampleOwnerIntent() {
        var owner=getOwner();
        if(owner==null || owner.level()!=level()) {
            previousOwnerPosition=null; observedOwner=null; movingUntil=0;
            return;
        }
        Vec3 position=owner.position();
        if(!owner.getUUID().equals(observedOwner)) {
            previousOwnerPosition=position; observedOwner=owner.getUUID();
            Vec3 view=owner.getViewVector(1).multiply(1,0,1);
            if(view.lengthSqr()>0.001) travelDirection=view.normalize();
        }
        if(previousOwnerPosition!=null) {
            Vec3 delta=position.subtract(previousOwnerPosition);
            if(delta.horizontalDistanceSqr()>0.0001 || Math.abs(delta.y)>0.03) {
                movingUntil=tickCount+10;
                Vec3 horizontal=delta.multiply(1,0,1);
                if(horizontal.lengthSqr()>0.0001) travelDirection=horizontal.normalize();
            }
        }
        previousOwnerPosition=position;
    }
    public LivingEntity getPriorityVictim() {
        if(resolvedVictim!=null && resolvedVictim.getHealth()<=0) { clearAttackOrder(); return null; }
        if(priorityVictim!=null && (resolvedVictim==null || resolvedVictim.isRemoved()))
            resolvedVictim=EntityReference.getLivingEntity(priorityVictim,level());
        if(resolvedVictim!=null && resolvedVictim.getHealth()<=0) { clearAttackOrder(); return null; }
        return resolvedVictim!=null && resolvedVictim.isAlive() && resolvedVictim.level()==level()?resolvedVictim:null;
    }
    public boolean hasAttackOrder() { getPriorityVictim(); return priorityVictim!=null; }
    private void clearAttackOrder() { priorityVictim=null; resolvedVictim=null; super.setTarget(null); }
    private void sampleOwnerAttack() {
        var owner=getOwner();
        if(owner!=null) {
            if(!owner.getUUID().equals(attackOwner)) { attackOwner=owner.getUUID(); lastOwnerAttackStamp=Integer.MIN_VALUE; }
            int stamp=owner.getLastHurtMobTimestamp();
            if(stamp!=lastOwnerAttackStamp) {
                lastOwnerAttackStamp=stamp;
                var victim=owner.getLastHurtMob();
                if(victim!=null && victim!=owner && !(victim instanceof ArnaldoEntity) && victim.isAlive()
                    && victim.level()==level() && super.canAttack(victim)
                    && (!(victim instanceof Player other) || !(owner instanceof Player player) || player.canHarmPlayer(other))) {
                    priorityVictim=EntityReference.of(victim); resolvedVictim=victim;
                    setOrderedToSit(false); setInSittingPose(false);
                }
            }
        }
        var victim=getPriorityVictim();
        if(victim!=null) super.setTarget(victim);
    }
    @Override public void setTarget(LivingEntity proposed) {
        var victim=getPriorityVictim();
        super.setTarget(victim!=null?victim:proposed);
    }
    @Override public void tick() {
        if(!level().isClientSide()) {
            if(isArmyRetired()) { removeByOwnerCommand(); return; }
            updateWeaponBaseDamage();
            sampleOwnerIntent();
            sampleOwnerAttack();
            if(tickCount%20==0) refreshNicknamePreference();
            if(godmode) {
                clearFire(); setAirSupply(getMaxAirSupply()); fallDistance=0;
                if(onGround() && !isInLava() && getY()>level().getMinY()) lastSafePosition=position();
                if(getY()<level().getMinY()-8) rescueFromVoid();
            }
        }
        super.tick();
        // Explicitly gifted items remain collectable with mobGriefing disabled too.
        if(level() instanceof ServerLevel server && !isRemoved() && !server.getGameRules().get(GameRules.MOB_GRIEFING)) collectNearbyEquipment(server);
    }
    private void enableEquipmentPickup() {
        setCanPickUpLoot(true);
        for(var slot:EquipmentSlot.VALUES) setDropChance(slot,2);
    }
    @Override public boolean wantsToPickUp(ServerLevel level,ItemStack stack) {
        if(stack.isEmpty()) return false;
        var slot=getEquipmentSlotForItem(stack);
        return slot!=EquipmentSlot.BODY && slot!=EquipmentSlot.SADDLE && isEquippableInSlot(stack,slot)
            && canReplaceCurrentItem(stack,getItemBySlot(slot),slot);
    }
    private void collectNearbyEquipment(ServerLevel level) {
        for(var item:level.getEntitiesOfClass(ItemEntity.class,getBoundingBox().inflate(1,0.5,1)))
            if(item.isAlive() && !item.hasPickUpDelay() && wantsToPickUp(level,item.getItem())) pickUpItem(level,item);
    }
    private boolean weaponInHand() {
        return getMainHandItem().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,ItemAttributeModifiers.EMPTY).modifiers()
            .stream().anyMatch(entry->entry.attribute().is(Attributes.ATTACK_DAMAGE) && entry.slot().test(EquipmentSlot.MAINHAND));
    }
    private void updateWeaponBaseDamage() {
        var attack=getAttribute(Attributes.ATTACK_DAMAGE);
        double base=weaponInHand()?1:4;
        if(attack!=null && attack.getBaseValue()!=base) attack.setBaseValue(base);
    }
    @Override public boolean isInvulnerableTo(ServerLevel level, DamageSource source) { return godmode || super.isInvulnerableTo(level,source); }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) { return !godmode && super.hurtServer(level,source,amount); }
    @Override public void kill(ServerLevel level) { if(!godmode) super.kill(level); }
    @Override protected void onBelowWorld() {
        if(godmode) { if(!level().isClientSide()) rescueFromVoid(); }
        else super.onBelowWorld();
    }
    private void rescueFromVoid() {
        var owner=getOwner();
        if(owner!=null && owner.level()==level() && owner.getY()>level().getMinY()) tryToTeleportToOwner();
        if(getY()<level().getMinY()) {
            Vec3 safe=lastSafePosition;
            if(safe==null) {
                int y=level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,getBlockX(),getBlockZ());
                safe=new Vec3(getX(),Math.max(y+1,level().getMinY()+4),getZ());
            }
            snapTo(safe.x,safe.y,safe.z,getYRot(),0);
        }
        setDeltaMovement(Vec3.ZERO); fallDistance=0;
    }
    @Override public boolean isFood(ItemStack stack) { return stack.is(Items.GLOW_BERRIES); }
    @Override public boolean canAttack(LivingEntity target) {
        return !isOrderedToSit() && super.canAttack(target);
    }
    @Override public boolean wantsToAttack(LivingEntity target, LivingEntity owner) {
        if (isOrderedToSit() || target instanceof Creeper || target instanceof Ghast) return false;
        if (target instanceof TamableAnimal pet && pet.isTame() && pet.isOwnedBy(owner)) return false;
        if (target instanceof Player other && owner instanceof Player player && !player.canHarmPlayer(other)) return false;
        return super.wantsToAttack(target, owner);
    }
    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isFood(stack)) {
            if (!isOwnedBy(player)) return InteractionResult.PASS;
            if(!level().isClientSide()) {
                setOrderedToSit(false); setInSittingPose(false);
                getNavigation().stop();
            }
            if (getHealth() < getMaxHealth()) {
                if (!level().isClientSide()) { heal(4.0F); usePlayerItem(player, hand, stack); }
                return InteractionResult.SUCCESS;
            }
            // Animal handles love hearts, growth, item consumption and cooldowns.
            InteractionResult result=super.mobInteract(player, hand);
            // Consume the interaction even during love/cooldown, so the empty off-hand cannot toggle sitting.
            return result.consumesAction() ? result : InteractionResult.SUCCESS;
        }
        // Let the game try the hand holding berries, without treating the other hand as a sit order.
        if(isFood(player.getMainHandItem()) || isFood(player.getOffhandItem())) return InteractionResult.PASS;
        if (isOwnedBy(player)) {
            if (!level().isClientSide()) {
                setOrderedToSit(!isOrderedToSit());
                setInSittingPose(isOrderedToSit());
                setJumping(false);
                getNavigation().stop();
                if(isOrderedToSit()) clearAttackOrder();
                setTarget(null);
            }
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }
    @Override public boolean canMate(Animal other) {
        return other instanceof ArnaldoEntity mate && !isOrderedToSit() && !mate.isOrderedToSit()
            && isTame() && mate.isTame() && super.canMate(other);
    }
    @Override public ArnaldoEntity getBreedOffspring(ServerLevel level, AgeableMob other) {
        ArnaldoEntity baby = ArnaldoContent.ARNALDO.create(level, EntitySpawnReason.BREEDING);
        if (baby != null) {
            baby.setOwnerReference(getOwnerReference());
            baby.setTame(true, false);
            baby.applyIdentity();
            baby.refreshNicknamePreference();
            baby.setHealth(baby.getMaxHealth());
        }
        return baby;
    }
    @Override protected SoundEvent getAmbientSound() { return null; }
    @Override protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.PLAYER_HURT; }
    @Override protected SoundEvent getDeathSound() { return SoundEvents.PLAYER_DEATH; }
    @Override public boolean doHurtTarget(ServerLevel level, Entity target) {
        updateWeaponBaseDamage();
        AttackAnimation.swing(this);
        boolean hit = super.doHurtTarget(level, target);
        if (hit) playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 1.0F);
        return hit;
    }
    @Override public boolean canBeLeashed() { return true; }
    private final class StayGoal extends Goal {
        StayGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP, Flag.LOOK)); }
        @Override public boolean canUse() { return isOrderedToSit() && !isInWater(); }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public void start() { getNavigation().stop(); setTarget(null); setInSittingPose(true); }
        @Override public void tick() { getNavigation().stop(); setTarget(null); }
        @Override public void stop() { setInSittingPose(false); }
    }
}
