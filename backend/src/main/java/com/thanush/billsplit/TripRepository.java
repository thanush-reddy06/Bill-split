package com.thanush.billsplit;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface TripRepository extends JpaRepository<Trip,Long> {
 List<Trip> findAllByOwnerIdOrderByCreatedAtDesc(Long ownerId);
 Optional<Trip> findByIdAndOwnerId(Long id,Long ownerId);
 @Modifying @Query("update Trip t set t.ownerId = :owner where t.ownerId is null")
 int assignLegacy(@Param("owner") Long owner);
}
