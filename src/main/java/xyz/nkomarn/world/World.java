package xyz.nkomarn.world;

import org.jetbrains.annotations.NotNull;
import xyz.nkomarn.Composter;
import xyz.nkomarn.entity.Entity;
import xyz.nkomarn.protocol.packet.s2c.TimeUpdateS2CPacket;
import xyz.nkomarn.type.Chunk;
import xyz.nkomarn.type.Location;
import xyz.nkomarn.world.generator.WorldGenerator;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class World {

    private final Location spawn = new Location(this, 0, 100, 0); // TODO implement for player spawning at some point
    // TODO entities list

    private final Composter server;
    private final Properties properties;
    private final ExecutorService thread;

    private final HashMap<Chunk.Key, Chunk> loadedChunks;
    private final HashMap<UUID, Entity> entities;

    private long time = 0;

    public World(@NotNull Composter server, @NotNull Properties properties, @NotNull ExecutorService thread) {
        this.server = server;
        this.properties = properties;
        this.thread = thread;
        this.loadedChunks = new HashMap<>();
        this.entities = new HashMap<>();
    }

    public UUID getUUID() {
        return properties.uuid;
    }

    public Chunk getChunkImmediately(int x, int z) {
        try {
            return getChunk(x, z).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    public CompletableFuture<Chunk> getChunk(int x, int z) {
        CompletableFuture<Chunk> future = new CompletableFuture<>();
        thread.submit(() -> {
            Chunk.Key key = new Chunk.Key(x, z);
            Chunk chunk = loadedChunks.get(key);

            if (chunk == null) {
                properties.getIO().read(x, z).thenAccept(futureChunk -> {
                    if (futureChunk != null) {
                        future.complete(futureChunk);
                        return;
                    }

                    Chunk newChunk = properties.getGenerator().generate(x, z);
                    loadedChunks.put(key, newChunk);
                    future.complete(newChunk);
                });
            } else {
                future.complete(chunk);
            }
        });
        return future;
    }

    public boolean isChunkLoaded(@NotNull Chunk.Key chunk) {
        return loadedChunks.containsKey(chunk);
    }

    public Location getSpawn() {
        return spawn;
    }

    public HashMap<UUID, Entity> getEntities() {
        return entities;
    }

    public void trackEntity(@NotNull Entity entity) {
        entities.put(entity.getUUID(), entity);
    }

    public void tick() {
        time += 1; // TODO change to just ++

        if (server.getTicks() % 20 == 0) {
            server.getPlayerManager().getPlayers().stream()
                    .filter(player -> player.getWorld().getUUID().equals(properties.uuid))
                    .forEach(player -> player.getSession().sendPacket(new TimeUpdateS2CPacket(time)));
        }
    }

    // TODO save chunks, etc

    public static class Properties {

        private final UUID uuid;
        private final ChunkIO io;
        private final WorldGenerator generator;

        public Properties(@NotNull UUID uuid, @NotNull ChunkIO io, @NotNull WorldGenerator generator) {
            this.uuid = uuid;
            this.io = io;
            this.generator = generator;
        }

        public @NotNull UUID getUUID() {
            return uuid;
        }

        public @NotNull ChunkIO getIO() {
            return io;
        }

        public @NotNull WorldGenerator getGenerator() {
            return generator;
        }
    }
}
