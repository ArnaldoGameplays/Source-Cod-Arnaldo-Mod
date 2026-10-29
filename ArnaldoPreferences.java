package br.com.arnaldo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;

/** One persistent nickname preference per owner, shared across dimensions and future companions. */
public final class ArnaldoPreferences extends SavedData {
    private final Set<UUID> hiddenOwners;
    private final Map<UUID,Long> removalGenerations;
    private final Map<UUID,Double> healthByOwner;
    private final Map<UUID,Boolean> godmodeByOwner;
    public static final Codec<ArnaldoPreferences> CODEC=RecordCodecBuilder.create(instance->instance.group(
        UUIDUtil.CODEC.listOf().optionalFieldOf("hidden_owners",List.of()).forGetter(data->List.copyOf(data.hiddenOwners)),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC,Codec.LONG).optionalFieldOf("removal_generations",Map.of())
            .forGetter(data->data.removalGenerations),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC,Codec.doubleRange(1,1024)).optionalFieldOf("health_by_owner",Map.of())
            .forGetter(data->data.healthByOwner),
        Codec.unboundedMap(UUIDUtil.STRING_CODEC,Codec.BOOL).optionalFieldOf("godmode_by_owner",Map.of())
            .forGetter(data->data.godmodeByOwner)
    ).apply(instance,ArnaldoPreferences::new));
    public static final SavedDataType<ArnaldoPreferences> TYPE=new SavedDataType<>(
        Identifier.withDefaultNamespace("arnaldo_preferences"),()->new ArnaldoPreferences(List.of(),Map.of(),Map.of(),Map.of()),
        CODEC,DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    private ArnaldoPreferences(List<UUID> owners,Map<UUID,Long> generations,Map<UUID,Double> health,Map<UUID,Boolean> godmode) {
        hiddenOwners=new HashSet<>(owners); removalGenerations=new HashMap<>(generations);
        healthByOwner=new HashMap<>(health); godmodeByOwner=new HashMap<>(godmode);
    }
    public double maxHealth(UUID owner) { return healthByOwner.getOrDefault(owner,20.0); }
    public boolean godmode(UUID owner) { return godmodeByOwner.getOrDefault(owner,false); }
    public void setMaxHealth(UUID owner,double health) { healthByOwner.put(owner,health); setDirty(); }
    public void setGodmode(UUID owner,boolean enabled) { godmodeByOwner.put(owner,enabled); setDirty(); }
    public long armyGeneration(UUID owner) { return removalGenerations.getOrDefault(owner,0L); }
    public void retireArmy(UUID owner) { removalGenerations.put(owner,armyGeneration(owner)+1); setDirty(); }
    public static ArnaldoPreferences get(ServerLevel level) {
        var overworld=level.getServer().overworld();
        var storage=(overworld==null?level:overworld).getDataStorage();
        var current=storage.get(TYPE);
        if(current!=null) return current;
        // 1.21.11 stored unnamespaced data files; keep old owner choices when upgrading a world.
        var legacy=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data/arnaldo_preferences.dat");
        if(java.nio.file.Files.isRegularFile(legacy)) {
            try {
                var tag=net.minecraft.nbt.NbtIo.readCompressed(legacy,net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                current=CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE,tag.get("data")).getOrThrow();
                storage.set(TYPE,current);
                return current;
            } catch(Exception failure) {
                com.mojang.logging.LogUtils.getLogger().warn("Could not import legacy Arnaldo preferences",failure);
            }
        }
        return storage.computeIfAbsent(TYPE);
    }
    public boolean nickVisible(UUID owner) { return !hiddenOwners.contains(owner); }
    public void setNickVisible(UUID owner,boolean visible) {
        if(visible?hiddenOwners.remove(owner):hiddenOwners.add(owner)) setDirty();
    }
}
