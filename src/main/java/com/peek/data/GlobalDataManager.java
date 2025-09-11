package com.peek.data;

import com.peek.PeekMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Objects;

public final class GlobalDataManager {
    private static final Map<Identifier, JsonCodecDataStorage<?>> STORAGES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Object> CACHE = new ConcurrentHashMap<>();

    private GlobalDataManager() {}

    public static <T> void register(Identifier id, JsonCodecDataStorage<T> storage) {
        STORAGES.put(id, storage);
        PeekMod.LOGGER.debug("Registered storage for id: {}", id);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getData(MinecraftServer server, Identifier id) {
        T cached = (T) CACHE.get(id);
        if (cached != null) {
            PeekMod.LOGGER.debug("Loaded data from cache for id: {}", id);
            return cached;
        }

        JsonCodecDataStorage<T> storage = (JsonCodecDataStorage<T>) STORAGES.get(id);
        if (storage == null) {
            PeekMod.LOGGER.warn("No storage found for id: {}", id);
            return null;
        }

        T data = null;
        try {
            data = storage.load(server);
            if (data != null) {
                CACHE.put(id, data);
                PeekMod.LOGGER.debug("Loaded data from storage for id: {}", id);
            } else {
                PeekMod.LOGGER.debug("No data found on disk for id: {}", id);
            }
        } catch (Exception e) {
            PeekMod.LOGGER.error("Error loading data for id: {}", id, e);
        }
        return data;
    }

    @SuppressWarnings("unchecked")
    public static <T> void setData(MinecraftServer server, Identifier id, T data) {
        JsonCodecDataStorage<T> storage = (JsonCodecDataStorage<T>) STORAGES.get(id);
        if (storage == null) {
            PeekMod.LOGGER.warn("No storage registered for id: {}, cannot save data", id);
            return;
        }

        try {
            boolean saved = storage.save(server, data);
            if (saved) {
                if (data != null) {
                    CACHE.put(id, data);
                    PeekMod.LOGGER.debug("Saved and cached data for id: {}", id);
                } else {
                    CACHE.remove(id);
                    PeekMod.LOGGER.debug("Removed cached data for id: {}", id);
                }
            } else {
                PeekMod.LOGGER.warn("Failed to save data for id: {}", id);
            }
        } catch (Exception e) {
            PeekMod.LOGGER.error("Exception while saving data for id: {}", id, e);
        }
    }

    public static void saveAll(MinecraftServer server) {
        Objects.requireNonNull(server, "Server must not be null");
        PeekMod.LOGGER.info("Saving all global cached data, total entries: {}", CACHE.size());
        for (Map.Entry<Identifier, Object> entry : CACHE.entrySet()) {
            try {
                setData(server, entry.getKey(), entry.getValue());
                PeekMod.LOGGER.debug("Saved global data for id: {}", entry.getKey());
            } catch (Exception e) {
                PeekMod.LOGGER.error("Failed to save global data for id: {}", entry.getKey(), e);
            }
        }
        PeekMod.LOGGER.info("Completed saving all global data");
    }
}
