package dev.srryo.ultimatum.kill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Persists identities that reached final erasure so a world reload cannot revive them. */
final class ExecutionTombstoneData extends SavedData {
    private static final String DATA_NAME = "ultimatum_execution_tombstones";
    private static final String UUIDS_KEY = "Uuids";

    private final Set<UUID> uuids = new HashSet<>();

    static ExecutionTombstoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ExecutionTombstoneData::load,
                ExecutionTombstoneData::new,
                DATA_NAME);
    }

    private static ExecutionTombstoneData load(CompoundTag tag) {
        ExecutionTombstoneData data = new ExecutionTombstoneData();
        ListTag values = tag.getList(UUIDS_KEY, Tag.TAG_STRING);
        for (Tag value : values) {
            try {
                data.uuids.add(UUID.fromString(value.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    boolean contains(UUID uuid) {
        return uuids.contains(uuid);
    }

    void add(UUID uuid) {
        if (uuids.add(uuid)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag values = new ListTag();
        for (UUID uuid : uuids) {
            values.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put(UUIDS_KEY, values);
        return tag;
    }
}
