package br.com.arnaldo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;

public final class ArnaldoCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("arnaldo").executes(ctx -> spawn(ctx.getSource(),1))
            .then(Commands.literal("health").then(Commands.argument("vida",DoubleArgumentType.doubleArg(1,1024))
                .executes(ctx->health(ctx.getSource(),DoubleArgumentType.getDouble(ctx,"vida")))))
            .then(Commands.literal("godmode").then(Commands.argument("ativo",BoolArgumentType.bool())
                .executes(ctx->godmode(ctx.getSource(),BoolArgumentType.getBool(ctx,"ativo")))))
            .then(Commands.literal("remove").executes(ctx->remove(ctx.getSource())))
            .then(Commands.argument("quantidade",IntegerArgumentType.integer(1))
                .executes(ctx->spawn(ctx.getSource(),IntegerArgumentType.getInteger(ctx,"quantidade"))))
            .then(Commands.literal("nick")
                .then(Commands.literal("hide").executes(ctx->nick(ctx.getSource(),false)))
                .then(Commands.literal("show").executes(ctx->nick(ctx.getSource(),true)))));
    }
    private static void updateArmy(CommandSourceStack source,ServerPlayer player,boolean refill) {
        for(var level:source.getServer().getAllLevels()) for(var entity:level.getAllEntities())
            if(entity instanceof ArnaldoEntity pet && pet.getOwnerReference()!=null
                && pet.getOwnerReference().getUUID().equals(player.getUUID())) {
                pet.refreshNicknamePreference();
                if(refill && pet.isAlive()) pet.setHealth(pet.getMaxHealth());
            }
    }
    private static int health(CommandSourceStack source,double health) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        ArnaldoPreferences.get(source.getLevel()).setMaxHealth(player.getUUID(),health);
        updateArmy(source,player,true);
        source.sendSuccess(()->Component.literal("Vida máxima dos seus Arnaldos: "+health+" pontos ("+(health/2)+" corações)."),false);
        return 1;
    }
    private static int godmode(CommandSourceStack source,boolean enabled) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        ArnaldoPreferences.get(source.getLevel()).setGodmode(player.getUUID(),enabled);
        updateArmy(source,player,false);
        source.sendSuccess(()->Component.literal("Godmode dos seus Arnaldos: "+enabled),false);
        return 1;
    }
    private static int remove(CommandSourceStack source) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        ArnaldoPreferences.get(source.getLevel()).retireArmy(player.getUUID());
        var army=new java.util.ArrayList<ArnaldoEntity>();
        for(var level:source.getServer().getAllLevels()) for(var entity:level.getAllEntities())
            if(entity instanceof ArnaldoEntity pet && pet.getOwnerReference()!=null
                && pet.getOwnerReference().getUUID().equals(player.getUUID())) army.add(pet);
        for(var pet:army) pet.removeByOwnerCommand();
        source.sendSuccess(()->Component.literal("Seus Arnaldos foram removidos ("+army.size()+" carregados). Os descarregados serão removidos ao carregar."),false);
        return army.size();
    }
    private static int nick(CommandSourceStack source,boolean visible) throws CommandSyntaxException {
        var player=source.getPlayerOrException();
        ArnaldoPreferences.get(source.getLevel()).setNickVisible(player.getUUID(),visible);
        for(var level:source.getServer().getAllLevels()) for(var entity:level.getAllEntities())
            if(entity instanceof ArnaldoEntity pet && pet.getOwnerReference()!=null
                && pet.getOwnerReference().getUUID().equals(player.getUUID())) pet.refreshNicknamePreference();
        source.sendSuccess(()->Component.literal(visible?"Nicks dos seus Arnaldos visíveis.":"Nicks dos seus Arnaldos ocultos."),false);
        return 1;
    }
    private static int spawn(CommandSourceStack source,int amount) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int created=0;
        for(int i=0;i<amount;i++) { if(!spawnOne(source,player)) break; created++; }
        final int count=created;
        if(count>0) source.sendSuccess(()->Component.literal(count+" Arnaldo(s) agora acompanham você!"),false);
        return count;
    }
    private static boolean spawnOne(CommandSourceStack source,ServerPlayer player) {
        var level = source.getLevel();
        ArnaldoEntity pet = ArnaldoContent.ARNALDO.create(level, EntitySpawnReason.COMMAND);
        if (pet == null) return false;
        boolean placed = false;
        // Leave enough vertical space for the human model, including below low ceilings.
        for (int dy = 0; dy <= 2 && !placed; dy++) {
            for (int dx = -2; dx <= 2 && !placed; dx++) {
                for (int dz = -2; dz <= 2 && !placed; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    pet.snapTo(player.getX()+dx, player.getY()+dy, player.getZ()+dz, player.getYRot(), 0);
                    placed = level.noCollision(pet) && level.getWorldBorder().isWithinBounds(pet.getBoundingBox())
                        && level.getBlockState(pet.blockPosition().below()).isSolidRender();
                }
            }
        }
        if (!placed) {
            source.sendFailure(Component.literal("Não há espaço seguro por perto para o Arnaldo. Tente em um local aberto."));
            return false;
        }
        pet.tame(player);
        pet.refreshNicknamePreference();
        pet.setHealth(pet.getMaxHealth());
        return level.addFreshEntity(pet);
    }
    private ArnaldoCommands() {}
}
