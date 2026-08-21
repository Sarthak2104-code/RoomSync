package com.roomsync.location.repository;

import com.roomsync.location.entity.Location;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {

    @Query("SELECT COUNT(l) > 0 FROM Location l WHERE LOWER(l.code) = LOWER(:code)")
    boolean existsByCodeIgnoreCase(@Param("code") String code);

    @Query("SELECT COUNT(l) > 0 FROM Location l WHERE LOWER(l.code) = LOWER(:code) AND l.id <> :id")
    boolean existsByCodeIgnoreCaseAndIdNot(@Param("code") String code, @Param("id") Long id);

    @Query("SELECT COUNT(l) > 0 FROM Location l WHERE LOWER(l.name) = LOWER(:name)")
    boolean existsByNameIgnoreCase(@Param("name") String name);

    @Query("SELECT COUNT(l) > 0 FROM Location l WHERE LOWER(l.name) = LOWER(:name) AND l.id <> :id")
    boolean existsByNameIgnoreCaseAndIdNot(@Param("name") String name, @Param("id") Long id);

    @Query("SELECT l FROM Location l WHERE LOWER(l.code) = LOWER(:code)")
    Optional<Location> findByCodeIgnoreCase(@Param("code") String code);

    @Query("SELECT l FROM Location l WHERE LOWER(l.name) = LOWER(:name)")
    Optional<Location> findByNameIgnoreCase(@Param("name") String name);

    Page<Location> findAllByActiveTrue(Pageable pageable);

    Optional<Location> findByIdAndActiveTrue(Long id);
}
