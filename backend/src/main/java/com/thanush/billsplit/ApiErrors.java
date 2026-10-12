package com.thanush.billsplit;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
 @ExceptionHandler(IllegalArgumentException.class)
 ResponseEntity<?> invalid(IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("message",e.getMessage())); }
 @ExceptionHandler(HttpMessageNotReadableException.class)
 ResponseEntity<?> malformed() { return ResponseEntity.badRequest().body(Map.of("message","Enter valid numbers and bill details.")); }
 @ExceptionHandler(ResponseStatusException.class)
 ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason())); }
 @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
 ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("message","This trip changed. Reopen it and try again.")); }
}
