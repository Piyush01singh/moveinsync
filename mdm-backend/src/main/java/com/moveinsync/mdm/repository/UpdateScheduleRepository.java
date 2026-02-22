package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.model.UpdateSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UpdateScheduleRepository extends JpaRepository<UpdateSchedule, Long> {
    @Query("""
            select s from UpdateSchedule s
            where (s.fromVersion = :fromVersion)
              and (s.targetRegion is null or (:region is not null and lower(s.targetRegion) = lower(:region)))
              and (
                    s.customizationTag is null
                    or (
                        :customizationTag is not null
                        and lower(s.customizationTag) = lower(:customizationTag)
                    )
                  )
              and (
                    s.targetDeviceGroup is null
                    or (
                        :deviceGroup is not null
                        and lower(s.targetDeviceGroup) = lower(:deviceGroup)
                    )
                  )
              and (s.isImmediate = true or (s.scheduledTime is not null and s.scheduledTime <= :now))
              and (s.approvalRequired = false or s.approvalStatus = 'APPROVED')
            order by s.id desc
            """)
    List<UpdateSchedule> findApplicableSchedules(
            @Param("fromVersion") String fromVersion,
            @Param("region") String region,
            @Param("customizationTag") String customizationTag,
            @Param("deviceGroup") String deviceGroup,
            @Param("now") LocalDateTime now
    );

    @Query("""
            select s from UpdateSchedule s
            where (s.isImmediate = true or (s.scheduledTime is not null and s.scheduledTime <= :now))
              and (s.approvalRequired = false or upper(s.approvalStatus) = 'APPROVED')
            """)
    List<UpdateSchedule> findActiveApprovedSchedules(@Param("now") LocalDateTime now);
}
