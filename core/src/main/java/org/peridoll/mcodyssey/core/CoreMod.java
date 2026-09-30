package org.peridoll.mcodyssey.core;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.util.WorldSavePath;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreMod implements ModInitializer {
    private static Database database;
    public static Database database() {
        if (database == null) throw new IllegalStateException("McOdyssey-Core is not running");
        return database;
    }
    public static CompletableFuture<Void> register(UUID id, String name) {
        return database().submit(c -> {
            try (var s = c.prepareStatement("INSERT INTO residents(uuid,name,first_join,last_join) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,last_join=excluded.last_join")) {
                long now = System.currentTimeMillis();
                s.setString(1,id.toString()); s.setString(2,name); s.setLong(3,now); s.setLong(4,now); s.executeUpdate();
            } return null;
        });
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                database = new Database(server.getSavePath(WorldSavePath.ROOT).resolve("mcodyssey/core.sqlite"));
                database.submit(c -> { try (var s = c.createStatement()) {
                    s.execute("PRAGMA journal_mode=WAL");
                    s.execute("CREATE TABLE IF NOT EXISTS residents(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,first_join INTEGER NOT NULL,last_join INTEGER NOT NULL)");
                } return null; }).join();
            } catch (Exception e) { throw new IllegalStateException("McOdyssey database startup failed",e); }
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) ->
            register(handler.player.getUuid(),handler.player.getGameProfile().getName()).exceptionally(e -> {
                org.slf4j.LoggerFactory.getLogger("McOdyssey-Core").error("Resident registration failed",e); return null;
            }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { if(database != null) { database.close(); database=null; } });
    }
}
