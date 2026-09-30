package org.peridoll.mcodyssey.eco;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.peridoll.mcodyssey.core.Database;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class EconomyTest {
 @TempDir Path dir;
 Database db; Economy eco; UUID a=UUID.randomUUID(), b=UUID.randomUUID();
 @BeforeEach void setup() throws Exception {
  db=new Database(dir.resolve("test.sqlite"));
  db.submit(c -> {try(var s=c.createStatement()) {s.execute("CREATE TABLE residents(uuid TEXT PRIMARY KEY,name TEXT)");}
   try(var s=c.prepareStatement("INSERT INTO residents VALUES(?,?)")){s.setString(1,a.toString());s.setString(2,"Alice");s.executeUpdate();s.setString(1,b.toString());s.setString(2,"Bob");s.executeUpdate();}return null;}).join();
  eco=new Economy(db); eco.initialize().join();
 }
 @AfterEach void close(){db.close();}
 @Test void transferAndPersistence() throws Exception {
  eco.pay(a,b,250).join(); assertEquals(750L,eco.balance(a).join()); assertEquals(1250L,eco.balance(b).join()); assertEquals(1,eco.history(a).join().size());
  db.close(); db=new Database(dir.resolve("test.sqlite"));eco=new Economy(db);eco.initialize().join();assertEquals(750L,eco.balance(a).join());
 }
 @Test void insufficientAndInvalidPaymentsDoNotChangeBalance(){
  assertThrows(CompletionException.class,()->eco.pay(a,b,1001).join());
  assertThrows(CompletionException.class,()->eco.pay(a,a,1).join());
  assertThrows(CompletionException.class,()->eco.pay(a,b,0).join());
  assertEquals(1000L,eco.balance(a).join());assertTrue(eco.history(a).join().isEmpty());
 }
 @Test void concurrentTransfersConserveMoney(){
  List<CompletableFuture<Void>> jobs=new ArrayList<>();for(int i=0;i<20;i++)jobs.add(eco.pay(a,b,100).exceptionally(e -> null));
  CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).join();
  assertEquals(0L,eco.balance(a).join());assertEquals(2000L,eco.balance(b).join());
 }
 @Test void recipientOverflowRollsBackDebit(){
  eco.balance(b).join();db.submit(c -> {try(var s=c.prepareStatement("UPDATE eco_accounts SET balance=? WHERE uuid=?")){s.setLong(1,Long.MAX_VALUE);s.setString(2,b.toString());s.executeUpdate();}return null;}).join();
  assertThrows(CompletionException.class,()->eco.pay(a,b,1).join());assertEquals(1000L,eco.balance(a).join());assertTrue(eco.history(a).join().isEmpty());
 }
}
