package org.peridoll.mcodyssey.eco;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.peridoll.mcodyssey.core.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
class CoreEcoIntegrationTest {
 @TempDir Path dir; Database db; Residents residents; Economy eco;
 UUID a=UUID.randomUUID(),b=UUID.randomUUID();
 @BeforeEach void setup() throws Exception {db=new Database(dir.resolve("core.sqlite"));residents=new Residents(db);residents.initialize().join();eco=new Economy(db);}
 @AfterEach void close(){db.close();}
 private void start(){eco.initialize().join();residents.register(a,"Alice").join();residents.register(b,"Bob").join();}
 @Test void IT01_coreAloneHasNoEconomyTables(){
  residents.register(a,"Alice").join();
  assertEquals(0,db.submit(c -> {try(var s=c.createStatement();var rs=s.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name LIKE 'eco_%'")){return rs.getInt(1);}}).join());
 }
 @Test void IT02_registerRenameWithoutResettingFirstJoin(){
  residents.register(a,"Alice").join();
  long first=db.submit(c -> {try(var s=c.createStatement();var rs=s.executeQuery("SELECT first_join FROM residents")){return rs.getLong(1);}}).join();
  residents.register(a,"Renamed").join();
  db.submit(c -> {try(var s=c.createStatement();var rs=s.executeQuery("SELECT name,first_join,last_join FROM residents")){assertTrue(rs.next());assertEquals("Renamed",rs.getString(1));assertEquals(first,rs.getLong(2));assertTrue(rs.getLong(3)>=first);assertFalse(rs.next());}return null;}).join();
 }
 @Test void IT03_repeatedInitializationAndRegistrationDoNotIssueMoneyAgain(){
  start();eco.pay(a,b,100).join();residents.initialize().join();eco.initialize().join();residents.register(a,"Alice").join();
  assertEquals(900L,eco.balance(a).join());assertEquals(1100L,eco.balance(b).join());
 }
 @Test void IT04_successfulTransferRecordsBothHistoryDirections(){
  start();eco.pay(a,b,250).join();assertEquals(750L,eco.balance(a).join());assertEquals(1250L,eco.balance(b).join());
  assertTrue(eco.history(a).join().getFirst().contains("送金 → Bob : 250"));assertTrue(eco.history(b).join().getFirst().contains("受取 ← Alice : 250"));
 }
 @Test void IT05_insufficientFundsRollBackAllChanges(){
  start();eco.balance(a).join();eco.balance(b).join();assertThrows(CompletionException.class,()->eco.pay(a,b,1001).join());
  assertEquals(1000L,eco.balance(a).join());assertEquals(1000L,eco.balance(b).join());assertTrue(eco.history(a).join().isEmpty());
 }
 @Test void IT06_concurrentTransfersHaveExactlyTenSuccesses(){
  start();eco.balance(a).join();eco.balance(b).join();List<CompletableFuture<Boolean>> jobs=new ArrayList<>();
  for(int i=0;i<20;i++)jobs.add(eco.pay(a,b,100).handle((v,e)->e==null));
  CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).join();assertEquals(10,jobs.stream().filter(CompletableFuture::join).count());
  assertEquals(0L,eco.balance(a).join());assertEquals(2000L,eco.balance(b).join());assertEquals(10,eco.history(a).join().size());
 }
 @Test void IT07_reopenDatabasePreservesBalancesAndLedger() throws Exception {
  start();eco.pay(a,b,250).join();db.close();db=new Database(dir.resolve("core.sqlite"));residents=new Residents(db);residents.initialize().join();eco=new Economy(db);eco.initialize().join();
  assertEquals(750L,eco.balance(a).join());assertEquals(1250L,eco.balance(b).join());assertEquals(1,eco.history(b).join().size());
 }
 @Test void IT08_historyIsNewestFirstLimitedToTenAndUsesCurrentName(){
  start();for(int i=1;i<=12;i++)eco.pay(a,b,i).join();residents.register(b,"NewBob").join();var history=eco.history(a).join();
  assertEquals(10,history.size());assertTrue(history.getFirst().startsWith("#12 "));assertTrue(history.getLast().startsWith("#3 "));assertTrue(history.getFirst().contains("NewBob"));
 }
 @Test void IT09_overflowRollsBackDebitAndLedger(){
  start();eco.balance(a).join();eco.balance(b).join();db.submit(c->{try(var s=c.prepareStatement("UPDATE eco_accounts SET balance=? WHERE uuid=?")){s.setLong(1,Long.MAX_VALUE);s.setString(2,b.toString());s.executeUpdate();}return null;}).join();
  assertThrows(CompletionException.class,()->eco.pay(a,b,1).join());assertEquals(1000L,eco.balance(a).join());assertEquals(Long.MAX_VALUE,eco.balance(b).join());assertTrue(eco.history(a).join().isEmpty());
 }
 @Test void IT10_unknownResidentIsRejectedByForeignKey(){
  start();assertThrows(CompletionException.class,()->eco.pay(a,UUID.randomUUID(),1).join());assertEquals(1000L,eco.balance(a).join());assertTrue(eco.history(a).join().isEmpty());
 }
}
