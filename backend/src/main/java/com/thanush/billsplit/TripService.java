package com.thanush.billsplit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static com.thanush.billsplit.SplitCalculator.*;

@Service
@Transactional
public class TripService {
 private final TripRepository repository;
 private final AccountRepository accounts;
 public TripService(TripRepository repository,AccountRepository accounts) { this.repository=repository;this.accounts=accounts; }
 private Long owner() {
  String email=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
  return accounts.findByEmail(email).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please log in.")).id;
 }
 public record SavedTrip(Long id, Long version, String title, String currency, Instant createdAt,
                         boolean settled, Calculation calculation) {}
 public record StatusRequest(Boolean settled, Long version) {}
 private Trip find(long id) {
  return repository.findByIdAndOwnerId(id,owner()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Trip not found."));
 }
 private void checkVersion(Trip trip, Long version) {
  if (!Objects.equals(trip.version,version))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"This trip changed. Reopen it before editing.");
 }
 private SavedTrip view(Trip t) {
  var input = new SplitRequest(t.title,t.currency,t.total,t.participants.stream()
     .map(p -> new Contribution(p.name,p.paid)).toList(),t.version,t.mode);
  return new SavedTrip(t.id,t.version,t.title,t.currency,t.createdAt,t.settled,calculate(input));
 }
 @Transactional(readOnly=true)
 public List<SavedTrip> list() {
  return repository.findAllByOwnerIdOrderByCreatedAtDesc(owner()).stream().map(this::view).toList();
 }
 @Transactional(readOnly=true)
 public SavedTrip get(long id) { return view(find(id)); }
 public SavedTrip save(Long id, SplitRequest input) {
  calculate(input);
  Trip t = id == null ? new Trip() : find(id);
  if (id != null) checkVersion(t,input.version());
  if (id == null) {t.createdAt=Instant.now();t.ownerId=owner();}
  t.mode=Objects.toString(input.mode(),"SETTLE");
  t.title=input.title().strip(); t.currency=input.currency(); t.total=input.total();
  t.settled=false;
  t.participants.clear();
  input.participants().forEach(p -> t.participants.add(new Trip.PaymentEntry(p.name().strip(),p.paid())));
  return view(repository.saveAndFlush(t));
 }
 public SavedTrip status(long id, StatusRequest request) {
  if (request == null || request.settled()==null) throw new IllegalArgumentException("Choose a settlement status.");
  Trip t=find(id); checkVersion(t,request.version()); t.settled=request.settled();
  return view(repository.saveAndFlush(t));
 }
 public void delete(long id) { repository.delete(find(id)); }
}
