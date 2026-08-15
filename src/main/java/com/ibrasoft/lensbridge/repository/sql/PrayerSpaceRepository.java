package com.ibrasoft.lensbridge.repository.sql;

import com.ibrasoft.lensbridge.model.board.Audience;
import com.ibrasoft.lensbridge.model.minbar.PrayerSpace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PrayerSpaceRepository extends JpaRepository<PrayerSpace, UUID> {

    @Query("SELECT ps FROM PrayerSpace ps WHERE ps.audience = :aud OR ps.audience = com.ibrasoft.lensbridge.model.board.Audience.BOTH ORDER BY ps.name ASC")
    List<PrayerSpace> findByAudienceOrBoth(@Param("aud") Audience audience);
}
