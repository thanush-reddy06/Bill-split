package com.thanush.billsplit;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class LegacyHistoryMigration implements ApplicationRunner {
 private final AccountRepository accounts; private final TripRepository trips; private final String email;
 public LegacyHistoryMigration(AccountRepository accounts,TripRepository trips,
  @Value("${app.legacy-owner-email:}") String email){this.accounts=accounts;this.trips=trips;this.email=email;}
 @Override @Transactional public void run(ApplicationArguments args){
  if(email.isBlank())return;
  Account owner=accounts.findByEmail(email.strip().toLowerCase(Locale.ROOT))
   .orElseThrow(()->new IllegalArgumentException("Register the legacy owner account first, then configure app.legacy-owner-email."));
  trips.assignLegacy(owner.id);
 }
}
