package com.thanush.billsplit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class TripApiTest {
 @Autowired MockMvc mvc;
 @Autowired ObjectMapper json;
 @Autowired AccountRepository accounts;
 @Autowired TripRepository trips;
 @Autowired PasswordEncoder encoder;
 private final String bill="""
 {"title":"Dinner","currency":"INR","total":"3000","mode":"SETTLE","participants":[
 {"name":"A","paid":"2000"},{"name":"B","paid":"1000"},{"name":"C","paid":"0"}]}
 """;
 private String register() throws Exception {
  String email=UUID.randomUUID()+"@example.com";
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(new AuthController.Registration("Student",email,"password123"))))
   .andExpect(status().isCreated());return email;
 }
 private MockHttpSession login(String email) throws Exception {
  return (MockHttpSession)mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","password123"))
   .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
 }
 @Test void registrationLoginValidationAndLogout() throws Exception {
  mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
  mvc.perform(post("/api/auth/register").contentType("application/json").content("{}"))
   .andExpect(status().isForbidden());
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
   .content("{\"name\":\"A\",\"email\":\"bad\",\"password\":\"password123\"}"))
   .andExpect(status().isBadRequest());
  String email=register();
  var account=accounts.findByEmail(email).orElseThrow();
  assertNotEquals("password123",account.passwordHash);assertTrue(encoder.matches("password123",account.passwordHash));
  mvc.perform(post("/api/auth/register").with(csrf()).contentType("application/json")
   .content(json.writeValueAsString(new AuthController.Registration("Student",email.toUpperCase(),"password123"))))
   .andExpect(status().isConflict());
  mvc.perform(post("/api/auth/login").with(csrf()).param("email",email).param("password","wrong"))
   .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Incorrect email or password."));
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  var session=login(email.toUpperCase());
  mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
   .andExpect(jsonPath("$.passwordHash").doesNotExist());
  mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
  assertTrue(session.isInvalid());
  mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
 }
 @Test void realCsrfTokenWorksAndLoginChangesSessionId() throws Exception {
  String email=register();
  var tokenResult=mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
  var session=(MockHttpSession)tokenResult.getRequest().getSession(false);String oldId=session.getId();
  var token=json.readTree(tokenResult.getResponse().getContentAsString());
  mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(),token.get("token").asText())
   .param("email",email).param("password","password123")).andExpect(status().isOk());
  assertNotEquals(oldId,session.getId());
  mvc.perform(get("/api/trips").session(session)).andExpect(status().isOk());
  mvc.perform(post("/api/trips").session(session).contentType("application/json").content(bill)).andExpect(status().isForbidden());
  var nextToken=mvc.perform(get("/api/auth/csrf").session(session)).andReturn();
  var next=json.readTree(nextToken.getResponse().getContentAsString());
  mvc.perform(post("/api/calculate").session(session).header(next.get("headerName").asText(),next.get("token").asText())
   .contentType("application/json").content(bill)).andExpect(status().isOk());
 }
 @Test void completeLifecycleAndVersionConflict() throws Exception {
  var session=login(register());
  var response=mvc.perform(post("/api/trips").session(session).with(csrf()).contentType("application/json").content(bill))
   .andExpect(status().isCreated()).andExpect(jsonPath("$.calculation.transfers[0].amountCents").value(100000)).andReturn();
  var trip=json.readTree(response.getResponse().getContentAsString());long id=trip.get("id").asLong(), version=trip.get("version").asLong();
  mvc.perform(get("/api/trips/"+id).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Dinner"));
  mvc.perform(patch("/api/trips/"+id+"/status").session(session).with(csrf()).contentType("application/json").content("{\"settled\":true,\"version\":"+version+"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.settled").value(true));
  mvc.perform(put("/api/trips/"+id).session(session).with(csrf()).contentType("application/json").content(bill.replace("\"title\":","\"version\":"+version+",\"title\":")))
   .andExpect(status().isConflict());
  var updated=mvc.perform(get("/api/trips/"+id).session(session)).andReturn();
  long next=json.readTree(updated.getResponse().getContentAsString()).get("version").asLong();
  mvc.perform(put("/api/trips/"+id).session(session).with(csrf()).contentType("application/json").content(bill.replace("\"title\":","\"version\":"+next+",\"title\":")))
   .andExpect(status().isOk()).andExpect(jsonPath("$.settled").value(false));
  mvc.perform(delete("/api/trips/"+id).session(session).with(csrf())).andExpect(status().isNoContent());
  mvc.perform(get("/api/trips/"+id).session(session)).andExpect(status().isNotFound());
 }
 @Test void everySavedBillEndpointChecksOwnership() throws Exception {
  var owner=login(register());var other=login(register());
  var saved=mvc.perform(post("/api/trips").session(owner).with(csrf()).contentType("application/json").content(bill)).andReturn();
  long id=json.readTree(saved.getResponse().getContentAsString()).get("id").asLong();
  mvc.perform(get("/api/trips").session(other)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(get("/api/trips/"+id).session(other)).andExpect(status().isNotFound());
  mvc.perform(put("/api/trips/"+id).session(other).with(csrf()).contentType("application/json").content(bill)).andExpect(status().isNotFound());
  mvc.perform(patch("/api/trips/"+id+"/status").session(other).with(csrf()).contentType("application/json").content("{\"settled\":true,\"version\":0}")).andExpect(status().isNotFound());
  mvc.perform(delete("/api/trips/"+id).session(other).with(csrf())).andExpect(status().isNotFound());
  mvc.perform(get("/api/trips/"+id).session(owner)).andExpect(status().isOk());
 }
 @Test void equalSplitPersistsAndPreviewDoesNotSave() throws Exception {
  var session=login(register());
  String equal=bill.replace("SETTLE","EQUAL").replace("3000","100").replace("2000","0").replace("1000","0");
  mvc.perform(post("/api/calculate").session(session).with(csrf()).contentType("application/json").content(equal))
   .andExpect(status().isOk()).andExpect(jsonPath("$.participants[0].shareCents").value(3334)).andExpect(jsonPath("$.unpaidCents").value(10000));
  mvc.perform(get("/api/trips").session(session)).andExpect(jsonPath("$.length()").value(0));
  mvc.perform(post("/api/trips").session(session).with(csrf()).contentType("application/json").content(equal)).andExpect(status().isCreated());
  mvc.perform(get("/api/trips").session(session)).andExpect(jsonPath("$[0].calculation.mode").value("EQUAL"));
  mvc.perform(post("/api/trips").session(session).with(csrf()).contentType("application/json").content(bill.replace("3000","4000")))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
  mvc.perform(post("/api/trips").session(session).with(csrf()).contentType("application/json").content("{bad"))
   .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists());
 }
 @Test void legacyBillsStayHiddenUntilAssignedByLocalConfiguration() throws Exception {
  String email=register();var session=login(email);
  var saved=mvc.perform(post("/api/trips").session(session).with(csrf()).contentType("application/json").content(bill)).andReturn();
  long id=json.readTree(saved.getResponse().getContentAsString()).get("id").asLong();
  Trip old=trips.findById(id).orElseThrow();old.ownerId=null;trips.saveAndFlush(old);
  mvc.perform(get("/api/trips").session(session)).andExpect(jsonPath("$.length()").value(0));
  // The production runner is transactional; supply one here because it is constructed directly.
  new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(s ->
   new LegacyHistoryMigration(accounts,trips,email).run(new DefaultApplicationArguments(new String[0])));
  mvc.perform(get("/api/trips/"+id).session(session)).andExpect(status().isOk());
 }
 @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
}
