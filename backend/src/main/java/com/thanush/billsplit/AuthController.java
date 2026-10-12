package com.thanush.billsplit;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;

@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AccountRepository accounts;
 private final PasswordEncoder encoder;
 public AuthController(AccountRepository accounts, PasswordEncoder encoder){this.accounts=accounts;this.encoder=encoder;}
 public record Registration(String name,String email,String password) {}
 public record Profile(Long id,String name,String email) {}
 @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token){
  return Map.of("token",token.getToken(),"headerName",token.getHeaderName());
 }
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
 public Map<String,String> register(@RequestBody Registration input){
  if(input.name()==null||input.name().isBlank()||input.name().strip().length()>60)
   throw new IllegalArgumentException("Enter your name (up to 60 characters).");
  String email=Objects.toString(input.email(),"").strip().toLowerCase(Locale.ROOT);
  if(email.length()>254||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
   throw new IllegalArgumentException("Enter a valid email address.");
  String password=input.password();
  if(password==null||password.length()<8||password.getBytes(StandardCharsets.UTF_8).length>72)
   throw new IllegalArgumentException("Use at least 8 characters and at most 72 UTF-8 bytes for your password.");
  if(accounts.existsByEmail(email))throw new ResponseStatusException(HttpStatus.CONFLICT,"This email is already registered.");
  Account a=new Account();a.name=input.name().strip();a.email=email;a.passwordHash=encoder.encode(password);
  try{accounts.saveAndFlush(a);}catch(DataIntegrityViolationException e){
   throw new ResponseStatusException(HttpStatus.CONFLICT,"This email is already registered.");
  }
  return Map.of("message","Account created. Please log in.");
 }
 @GetMapping("/me") public Profile me(Principal principal){
  Account a=accounts.findByEmail(principal.getName()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please log in."));
  return new Profile(a.id,a.name,a.email);
 }
}
