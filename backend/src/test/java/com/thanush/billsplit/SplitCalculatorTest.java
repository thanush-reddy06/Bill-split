package com.thanush.billsplit;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.thanush.billsplit.SplitCalculator.*;

class SplitCalculatorTest {
 private Contribution p(String name,String paid){return new Contribution(name,new BigDecimal(paid));}
 private SplitRequest bill(String total,Contribution... people){return new SplitRequest("Dinner","INR",new BigDecimal(total),List.of(people),null);}
 @Test void calculatesRequestedExample(){
  var c=calculate(bill("3000",p("A","1800"),p("B","900"),p("C","300")));
  assertEquals(List.of(new Transfer("B","A",10000),new Transfer("C","A",70000)),c.transfers());
 }
 @Test void keepsRoundingExact(){
  var c=calculate(bill("100",p("A","100"),p("B","0"),p("C","0")));
  assertEquals(List.of(3334L,3333L,3333L),c.participants().stream().map(Person::shareCents).toList());
  assertEquals(6666,c.transfers().stream().mapToLong(Transfer::amountCents).sum());
 }
 @Test void equalContributionsNeedNoTransfers(){
  assertTrue(calculate(bill("20",p("A","10"),p("B","10"))).transfers().isEmpty());
 }
 @Test void rejectsUnbalancedTotalsAndDuplicateNames(){
  assertThrows(IllegalArgumentException.class,()->calculate(bill("20",p("A","10"),p("B","9"))));
  assertThrows(IllegalArgumentException.class,()->calculate(bill("20",p("A","10"),p(" a ","10"))));
 }
 @Test void rejectsInvalidMoney(){
  assertThrows(IllegalArgumentException.class,()->calculate(bill("0",p("A","0"),p("B","0"))));
  assertThrows(IllegalArgumentException.class,()->calculate(bill("20",p("A","-1"),p("B","21"))));
  assertThrows(IllegalArgumentException.class,()->calculate(bill("20.001",p("A","20.001"),p("B","0"))));
 }
 @Test void handlesOneCentAndManyPeople(){
  var c=calculate(bill(".01",p("A","0"),p("B","0"),p("C",".01")));
  assertEquals(List.of(new Transfer("A","C",1)),c.transfers());
 }
 @Test void randomizedTransfersExactlySettleAllBalances(){
  Random rng=new Random(42);
  for(int test=0;test<1000;test++){
   int n=2+rng.nextInt(49);long total=0;List<Contribution> inputs=new ArrayList<>();
   for(int i=0;i<n;i++){long paid=rng.nextInt(100000);total+=paid;inputs.add(new Contribution("P"+i,BigDecimal.valueOf(paid,2)));}
   var c=calculate(new SplitRequest("Trip","USD",BigDecimal.valueOf(total,2),inputs,null));
   assertEquals(total,c.participants().stream().mapToLong(Person::shareCents).sum());
   Map<String,Long> balances=new HashMap<>();c.participants().forEach(p->balances.put(p.name(),p.balanceCents()));
   for(var t:c.transfers()){
    assertTrue(t.amountCents()>0);assertNotEquals(t.from(),t.to());
    balances.compute(t.from(),(k,v)->v+t.amountCents());balances.compute(t.to(),(k,v)->v-t.amountCents());
   }
   assertTrue(balances.values().stream().allMatch(v->v==0));
   assertTrue(c.transfers().size()<=n-1);
  }
 }
 @Test void zeroPayerOwesFullShareToPersonWhoCoveredIt(){
  var c=calculate(bill("3000",p("Thanush","2000"),p("Arjun","1000"),p("Sai","0")));
  assertEquals(-100000,c.participants().get(2).balanceCents());
  assertEquals(List.of(new Transfer("Sai","Thanush",100000)),c.transfers());
 }
 @Test void unpaidBillDividesExactlyWithoutInventingARecipient(){
  var c=calculate(new SplitRequest("Dinner","INR",new BigDecimal("100"),List.of(p("A","0"),p("B","0"),p("C","0")),null,"EQUAL"));
  assertEquals(List.of(3334L,3333L,3333L),c.participants().stream().map(Person::shareCents).toList());
  assertEquals(10000,c.unpaidCents());assertTrue(c.transfers().isEmpty());
  assertThrows(IllegalArgumentException.class,()->calculate(new SplitRequest("Dinner","INR",new BigDecimal("100"),List.of(p("A","100"),p("B","0")),null,"EQUAL")));
 }
}
