package com.thanush.billsplit;
import java.util.Locale;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
 @Bean UserDetailsService users(AccountRepository repository) {
  return email -> repository.findByEmail(email.strip().toLowerCase(Locale.ROOT))
   .map(a -> User.withUsername(a.email).password(a.passwordHash).roles("USER").build())
   .orElseThrow(() -> new UsernameNotFoundException("Incorrect email or password."));
 }
 @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
  return http
   .authorizeHttpRequests(a -> a
    .requestMatchers("/api/health","/api/auth/csrf","/api/auth/register","/api/auth/login").permitAll()
    .requestMatchers("/api/**").authenticated().anyRequest().permitAll())
   .requestCache(c -> c.disable())
   .formLogin(f -> f.loginProcessingUrl("/api/auth/login").usernameParameter("email")
    .successHandler((req,res,auth) -> {res.setContentType("application/json");res.getWriter().write("{\"message\":\"Logged in\"}");})
    .failureHandler((req,res,e) -> {res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Incorrect email or password.\"}");}))
   .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
    .logoutSuccessHandler((req,res,auth)->res.setStatus(204)))
   .exceptionHandling(e -> e
    .authenticationEntryPoint((req,res,ex)->{res.setStatus(401);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Please log in to continue.\"}");})
    .accessDeniedHandler((req,res,ex)->{res.setStatus(403);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Your session token expired. Please try again.\"}");}))
   .build();
 }
}
