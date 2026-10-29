package br.com.arnaldo.client;

import br.com.arnaldo.ArnaldoContent;
import br.com.arnaldo.ArnaldoEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.HumanoidArm;

public final class ArnaldoRenderer extends HumanoidMobRenderer<ArnaldoEntity, ArnaldoRenderer.State, ArnaldoRenderer.Model> {
    private static final Identifier TEXTURE = ArnaldoContent.id("textures/entity/arnaldo.png");
    public ArnaldoRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(layer(false)), new Model(layer(true)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this,
            ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR,context.getModelSet(),Model::new),
            ArmorModelSet.bake(ModelLayers.ZOMBIE_BABY_ARMOR,context.getModelSet(),Model::new),context.getEquipmentRenderer()));
    }
    @Override protected HumanoidModel.ArmPose getArmPose(ArnaldoEntity entity,HumanoidArm arm) {
        var pose=super.getArmPose(entity,arm);
        return pose!=HumanoidModel.ArmPose.EMPTY?pose:entity.getItemHeldByArm(arm).isEmpty()?pose:HumanoidModel.ArmPose.ITEM;
    }
    private static ModelPart layer(boolean baby) {
        var layer = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64);
        // The vanilla humanoid baby transformer is also used by baby zombies.
        return (baby ? layer.apply(HumanoidModel.BABY_TRANSFORMER) : layer).bakeRoot();
    }
    @Override public State createRenderState() { return new State(); }
    @Override public Identifier getTextureLocation(State state) { return TEXTURE; }
    @Override protected net.minecraft.network.chat.Component getNameTag(ArnaldoEntity entity) {
        // Same vanilla name-tag submission used for players, with an explicit white component.
        return entity.isCustomNameVisible()?ArnaldoEntity.playerName():null;
    }
    @Override protected boolean shouldShowName(ArnaldoEntity entity,double distance) {
        return entity.isCustomNameVisible() && super.shouldShowName(entity,distance);
    }
    @Override public void extractRenderState(ArnaldoEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.sitting = entity.isInSittingPose();
    }
    public static final class State extends HumanoidRenderState { public boolean sitting; }
    public static final class Model extends HumanoidModel<State> {
        public Model(ModelPart root) { super(root); }
        @Override public void setupAnim(State state) {
            super.setupAnim(state);
            if (state.sitting) {
                // Human seated pose: body upright, legs forward, arms resting at the sides.
                float offset = state.isBaby ? 6.0F : 12.0F;
                head.y += offset; body.y += offset;
                rightArm.y += offset; leftArm.y += offset;
                rightLeg.y += offset; leftLeg.y += offset;
                rightLeg.xRot = leftLeg.xRot = -(float)Math.PI / 2;
                rightLeg.yRot = 0.12F; leftLeg.yRot = -0.12F;
                rightLeg.zRot = leftLeg.zRot = 0;
                rightArm.xRot = leftArm.xRot = -0.12F;
                rightArm.zRot = 0.06F; leftArm.zRot = -0.06F;
            }
        }
    }
}
