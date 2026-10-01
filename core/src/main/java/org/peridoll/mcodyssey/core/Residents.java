package org.peridoll.mcodyssey.core;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Shared resident persistence, independent of Minecraft and economy. */
public final class Residents {
    private final Database database;
    public Residents(Database database) { this.database = database; }
    public CompletableFuture<Void> initialize() {
        return database.submit(c -> {
            try (var s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("CREATE TABLE IF NOT EXISTS residents(uuid TEXT PRIMARY KEY,name TEXT NOT NULL,first_join INTEGER NOT NULL,last_join INTEGER NOT NULL)");
            }
            return null;
        });
    }
    public CompletableFuture<Void> register(UUID id, String name) {
        return database.submit(c -> {
            try (var s = c.prepareStatement("INSERT INTO residents(uuid,name,first_join,last_join) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET name=excluded.name,last_join=excluded.last_join")) {
                long now = System.currentTimeMillis();
                s.setString(1, id.toString()); s.setString(2, name);
                s.setLong(3, now); s.setLong(4, now); s.executeUpdate();
            }
            return null;
        });
    }
}
