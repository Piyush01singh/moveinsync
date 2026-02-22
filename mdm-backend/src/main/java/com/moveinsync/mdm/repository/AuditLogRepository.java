package com.moveinsync.mdm.repository;

import com.moveinsync.mdm.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByDeviceImeiOrderByTimestampDesc(String deviceImei);

    List<AuditLog> findByDeviceImeiOrderByTimestampAsc(String deviceImei);

    List<AuditLog> findByScheduleIdInOrderByTimestampAsc(List<Long> scheduleIds);

    List<AuditLog> findAllByOrderByIdAsc();

    AuditLog findTopByOrderByIdDesc();

    long countByAction(String action);

    @Query("""
            select coalesce(a.failureStage, 'UNKNOWN') as label, count(a) as total
            from AuditLog a
            where a.action = 'FAILED'
            group by a.failureStage
            """)
    List<LabelCountProjection> countFailuresByStage();
}
