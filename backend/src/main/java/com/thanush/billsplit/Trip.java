package com.thanush.billsplit;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name="trips")
public class Trip {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Version public Long version;
 public Long ownerId;
 @Column(nullable=false,columnDefinition="varchar(10) default 'SETTLE'") public String mode="SETTLE";
 @Column(nullable=false,length=80) public String title;
 @Column(nullable=false,length=3) public String currency;
 @Column(nullable=false,precision=14,scale=2) public BigDecimal total;
 @Column(nullable=false) public Instant createdAt;
 @Column(nullable=false) public boolean settled;
 @ElementCollection
 @CollectionTable(name="trip_participants",joinColumns=@JoinColumn(name="trip_id"))
 @OrderColumn(name="participant_order")
 public List<PaymentEntry> participants = new ArrayList<>();
 @Embeddable
 public static class PaymentEntry {
  @Column(nullable=false,length=40) public String name;
  @Column(nullable=false,precision=14,scale=2) public BigDecimal paid;
  public PaymentEntry() {}
  public PaymentEntry(String name, BigDecimal paid) { this.name=name; this.paid=paid; }
 }
}
