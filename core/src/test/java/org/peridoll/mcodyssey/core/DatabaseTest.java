package org.peridoll.mcodyssey.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseTest {
 @TempDir Path dir;
 Database db;
 @BeforeEach void setup() throws Exception { db=new Database(dir.resolve("nested/core.sqlite")); }
 @AfterEach void close() { db.close(); }
 @Test void UT_C01_workerThreadAndOrdering() {
  String caller=Thread.currentThread().getName();
  assertNotEquals(caller,db.submit(c -> Thread.currentThread().getName()).join());
  var first=db.submit(c -> {try(var s=c.createStatement()){s.execute("CREATE TABLE ordered(id INTEGER)");s.execute("INSERT INTO ordered VALUES(7)");}return null;});
  var second=db.submit(c -> {try(var s=c.createStatement();var rs=s.executeQuery("SELECT id FROM ordered")){return rs.getInt(1);}});
  first.join();assertEquals(7,second.join());
 }
 @Test void UT_C02_commitAndRollback() {
  db.submit(c -> {try(var s=c.createStatement()){s.execute("CREATE TABLE totals(n INTEGER)");s.execute("INSERT INTO totals VALUES(1)");}return null;}).join();
  db.transaction(c -> {try(var s=c.createStatement()){s.execute("UPDATE totals SET n=2");}return null;}).join();
  assertThrows(CompletionException.class,()->db.transaction(c -> {try(var s=c.createStatement()){s.execute("UPDATE totals SET n=9");}throw new IllegalStateException("forced failure");}).join());
  assertEquals(2,db.submit(c -> {try(var s=c.createStatement();var rs=s.executeQuery("SELECT n FROM totals")){return rs.getInt(1);}}).join());
 }
 @Test void UT_C03_foreignKeysEnabled() {
  assertEquals(1,db.submit(c -> {try(var s=c.createStatement();var rs=s.executeQuery("PRAGMA foreign_keys")){return rs.getInt(1);}}).join());
 }
 @Test void UT_C04_shutdownDrainsQueuedWrites() {
  var pending=db.submit(c -> {try(var s=c.createStatement()){s.execute("CREATE TABLE completed(n INTEGER)");s.execute("INSERT INTO completed VALUES(1)");}return null;});
  db.close();assertTrue(pending.isDone());pending.join();
  assertThrows(RejectedExecutionException.class,()->db.submit(c -> null));
 }
}
