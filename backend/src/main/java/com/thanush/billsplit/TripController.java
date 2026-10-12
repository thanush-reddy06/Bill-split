package com.thanush.billsplit;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.thanush.billsplit.SplitCalculator.*;
import static com.thanush.billsplit.TripService.*;

@RestController
@RequestMapping("/api")
public class TripController {
 private final TripService service;
 public TripController(TripService service) { this.service=service; }
 @GetMapping("/health") public Map<String,String> health() { return Map.of("status","UP"); }
 @PostMapping("/calculate") public Calculation preview(@RequestBody SplitRequest input) { return calculate(input); }
 @GetMapping("/trips") public List<SavedTrip> list() { return service.list(); }
 @GetMapping("/trips/{id}") public SavedTrip get(@PathVariable long id) { return service.get(id); }
 @PostMapping("/trips") @ResponseStatus(HttpStatus.CREATED)
 public SavedTrip create(@RequestBody SplitRequest input) { return service.save(null,input); }
 @PutMapping("/trips/{id}") public SavedTrip edit(@PathVariable long id,@RequestBody SplitRequest input) { return service.save(id,input); }
 @PatchMapping("/trips/{id}/status")
 public SavedTrip status(@PathVariable long id,@RequestBody StatusRequest input) { return service.status(id,input); }
 @DeleteMapping("/trips/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable long id) { service.delete(id); }
}
