package org.peridoll.mcodyssey.eco;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;
class EconomyValidationTest {
 private void rejected(long amount, boolean self) {
  var a=UUID.randomUUID();var b=self?a:UUID.randomUUID();
  // Null DB proves invalid input is rejected before any persistence access.
  var error=assertThrows(CompletionException.class,()->new Economy(null).pay(a,b,amount).join());
  assertInstanceOf(IllegalArgumentException.class,error.getCause());
 }
 @Test void UT_E01_zero(){rejected(0,false);}
 @Test void UT_E02_negative(){rejected(-1,false);}
 @Test void UT_E03_selfTransfer(){rejected(1,true);}
}
