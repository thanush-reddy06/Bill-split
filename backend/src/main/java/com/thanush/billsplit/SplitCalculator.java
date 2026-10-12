package com.thanush.billsplit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class SplitCalculator {
 public record Contribution(String name, BigDecimal paid) {}
 public record SplitRequest(String title, String currency, BigDecimal total,
                            List<Contribution> participants, Long version, String mode) {
  public SplitRequest(String title,String currency,BigDecimal total,List<Contribution> participants,Long version){
   this(title,currency,total,participants,version,"SETTLE");
  }
 }
 public record Person(String name, long paidCents, long shareCents, long balanceCents) {}
 public record Transfer(String from, String to, long amountCents) {}
 public record Calculation(long totalCents, List<Person> participants, List<Transfer> transfers, String mode, long unpaidCents) {}

 public static long cents(BigDecimal value, String field) {
  if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("1000000000")) > 0)
   throw new IllegalArgumentException(field + " must be between 0 and 1,000,000,000.");
  try { return value.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact(); }
  catch (ArithmeticException e) { throw new IllegalArgumentException(field + " must have at most two decimal places."); }
 }
 public static Calculation calculate(SplitRequest r) {
  if (r == null) throw new IllegalArgumentException("Enter the bill details.");
  if (r.title() == null || r.title().isBlank() || r.title().strip().length() > 80)
   throw new IllegalArgumentException("Enter a trip or meal name with 1–80 characters.");
  if (!Set.of("INR", "USD", "EUR", "GBP").contains(Objects.toString(r.currency(), "")))
   throw new IllegalArgumentException("Select a supported currency.");
  String mode=Objects.toString(r.mode(),"SETTLE");
  if(!Set.of("EQUAL","SETTLE").contains(mode))throw new IllegalArgumentException("Choose a split mode.");
  long total = cents(r.total(), "Total expense");
  if (total == 0) throw new IllegalArgumentException("Total expense must be greater than zero.");
  if (r.participants() == null || r.participants().size() < 2 || r.participants().size() > 50)
   throw new IllegalArgumentException("Add between 2 and 50 people.");
  Set<String> names = new HashSet<>();
  long sum = 0;
  for (Contribution p : r.participants()) {
   if (p == null || p.name() == null || p.name().isBlank() || p.name().strip().length() > 40)
    throw new IllegalArgumentException("Each person needs a name with 1–40 characters.");
   if (!names.add(p.name().strip().toLowerCase(Locale.ROOT)))
    throw new IllegalArgumentException("Use a different name for each person.");
   sum += cents(p.paid(), "Amount paid");
  }
  if (mode.equals("EQUAL") && sum != 0) throw new IllegalArgumentException("Use Settle payments when someone has already paid.");
  if (mode.equals("SETTLE") && sum != total) throw new IllegalArgumentException("The amounts already paid must add up to the total expense.");
  int n = r.participants().size();
  List<Person> people = new ArrayList<>();
  long[] balance = new long[n];
  for (int i = 0; i < n; i++) {
   Contribution p = r.participants().get(i);
   // Distribute remainder cents in input order so the shares sum exactly to the bill.
   long share = total / n + (i < total % n ? 1 : 0);
   long paid = cents(p.paid(), "Amount paid");
   balance[i] = paid - share;
   people.add(new Person(p.name().strip(), paid, share, balance[i]));
  }
  List<Transfer> transfers = new ArrayList<>();
  if(mode.equals("EQUAL"))return new Calculation(total,List.copyOf(people),List.of(),mode,total);
  int debtor = 0, creditor = 0;
  while (true) {
   while (debtor < n && balance[debtor] >= 0) debtor++;
   while (creditor < n && balance[creditor] <= 0) creditor++;
   if (debtor == n || creditor == n) break;
   long amount = Math.min(-balance[debtor], balance[creditor]);
   transfers.add(new Transfer(people.get(debtor).name(), people.get(creditor).name(), amount));
   balance[debtor] += amount;
   balance[creditor] -= amount;
  }
  return new Calculation(total, List.copyOf(people), List.copyOf(transfers),mode,0);
 }
}
