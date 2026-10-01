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
        return new Residents(database()).register(id, name);
    }

    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            try {
                database = new Database(server.getSavePath(WorldSavePath.ROOT).resolve("mcodyssey/core.sqlite"));
                new Residents(database).initialize().join();
            } catch (Exception e) { throw new IllegalStateException("McOdyssey database startup failed",e); }
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) ->
            register(handler.player.getUuid(),handler.player.getGameProfile().getName()).exceptionally(e -> {
                org.slf4j.LoggerFactory.getLogger("McOdyssey-Core").error("Resident registration failed",e); return null;
            }));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> { if(database != null) { database.close(); database=null; } });
    }
}
