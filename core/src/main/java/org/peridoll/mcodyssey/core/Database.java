package org.peridoll.mcodyssey.core;

import java.nio.file.*;
import java.sql.*;
import java.util.concurrent.*;

/** One ordered worker: no database I/O on the Minecraft tick thread. */
public final class Database implements AutoCloseable {
    @FunctionalInterface public interface Work<T> { T run(Connection connection) throws Exception; }
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "McOdyssey-DB"));
    private final String url;
    public Database(Path file) throws Exception {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Class.forName("org.sqlite.JDBC");
        url = "jdbc:sqlite:" + file.toAbsolutePath();
    }
    public <T> CompletableFuture<T> submit(Work<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
                return work.run(c);
            } catch (Exception e) { throw new CompletionException(e); }
        }, worker);
    }
    public <T> CompletableFuture<T> transaction(Work<T> work) {
        return submit(c -> {
            c.setAutoCommit(false);
            try { T result = work.run(c); c.commit(); return result; }
            catch (Exception e) { c.rollback(); throw e; }
        });
    }
    @Override public void close() {
        worker.shutdown();
        try { if (!worker.awaitTermination(30, TimeUnit.SECONDS)) worker.shutdownNow(); }
        catch (InterruptedException e) { worker.shutdownNow(); Thread.currentThread().interrupt(); }
    }
}
