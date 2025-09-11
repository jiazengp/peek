package com.peek.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.peek.PeekMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record JsonCodecDataStorage<T>(String path, Codec<T> codec) {
    private final static String DIR = "global-mod-data";

    public boolean save(MinecraftServer server, T data) {
        Path globalPath = server.getSavePath(WorldSavePath.ROOT).resolve(DIR);
        Path filePath = globalPath.resolve(this.path + ".json");

        if (data == null) {
            try {
                return Files.deleteIfExists(filePath);
            } catch (IOException e) {
                PeekMod.LOGGER.error("Failed to delete global data file at {}\n{}", filePath, e.fillInStackTrace());
                return false;
            }
        }

        try {
            if (!Files.exists(globalPath)) {
                Files.createDirectories(globalPath);
            }

            var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(server);
            if (registryManager == null) {
                PeekMod.LOGGER.error("Cannot save global data - server registry manager not available");
                return false;
            }
            
            var encoded = codec.encodeStart(registryManager.getOps(JsonOps.INSTANCE), data)
                    .getOrThrow();

            Files.writeString(filePath, encoded.toString(), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            PeekMod.LOGGER.error("IOException while saving global data for path {}\n{}", this.path, e.fillInStackTrace());
            return false;
        } catch (Exception e) {
            PeekMod.LOGGER.error("Unexpected error while saving global data for path {}\n{}", this.path, e.fillInStackTrace());
            return false;
        }
    }

    public T load(MinecraftServer server) {
        Path filePath = server.getSavePath(WorldSavePath.ROOT).resolve(DIR).resolve(this.path + ".json");
        if (!Files.exists(filePath)) {
            return null;
        }

        try {
            String jsonString = Files.readString(filePath, StandardCharsets.UTF_8);
            JsonElement element = JsonParser.parseString(jsonString);

            var registryManager = com.peek.utils.compat.PlayerCompat.getRegistryManager(server);
            if (registryManager == null) {
                PeekMod.LOGGER.error("Cannot load global data - server registry manager not available");
                return null;
            }
            
            var decoded = codec.decode(registryManager.getOps(JsonOps.INSTANCE), element);

            if (decoded.result().isEmpty()) {
                PeekMod.LOGGER.error("Decoding failed or returned empty for global data at path {}", this.path);
                return null;
            }

            return decoded.result().map(Pair::getFirst).orElse(null);
        } catch (IOException e) {
            PeekMod.LOGGER.error("IOException while loading global data for path {}", this.path, e);
            return null;
        } catch (Exception e) {
            PeekMod.LOGGER.error("Unexpected error while loading global data for path {}", this.path, e);
            return null;
        }
    }
}
