package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.model.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, String> {
    Optional<Device> findByImeiNumber(String imeiNumber);

    long countByLocationRegionIgnoreCaseAndAppVersion(String locationRegion, String appVersion);

    long countByLastAppOpenTimeAfter(LocalDateTime threshold);

    List<Device> findTop10ByLastAppOpenTimeBeforeOrderByLastAppOpenTimeAsc(LocalDateTime threshold);

    @Query("select d.appVersion as label, count(d) as total from Device d group by d.appVersion")
    List<LabelCountProjection> countByVersion();

    @Query("select d.locationRegion as label, count(d) as total from Device d group by d.locationRegion")
    List<LabelCountProjection> countByRegion();

    @Query("select coalesce(d.deviceGroup, 'UNASSIGNED') as label, count(d) as total from Device d group by d.deviceGroup")
    List<LabelCountProjection> countByDeviceGroup();

    @Query("""
            select d.locationRegion as region, d.appVersion as version, count(d) as total
            from Device d
            group by d.locationRegion, d.appVersion
            """)
    List<RegionVersionCountProjection> countByRegionAndVersion();

    @Query("""
            select count(d)
            from Device d
            where d.appVersion = :version
              and (:region is null or lower(d.locationRegion) = :region)
              and (:customizationTag is null or lower(coalesce(d.customizationTag, '')) = :customizationTag)
              and (:deviceGroup is null or lower(coalesce(d.deviceGroup, '')) = :deviceGroup)
            """)
    long countInScopeByVersion(
            @Param("version") String version,
            @Param("region") String region,
            @Param("customizationTag") String customizationTag,
            @Param("deviceGroup") String deviceGroup
    );

    @Query("select d from Device d where d.locationRegion = :region and d.appVersion = :version")
    List<Device> findByRegionAndVersion(@Param("region") String region, @Param("version") String version);
}
