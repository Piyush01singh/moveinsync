package com.moveinsync.mdm.config;

import com.moveinsync.mdm.dto.AuditIntegrityResponse;
import com.moveinsync.mdm.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditImmutabilityInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final AuditLogService auditLogService;

    @Value("${mdm.audit.enforce-immutability:true}")
    private boolean enforceImmutability;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!enforceImmutability) {
            log.info("Audit immutability enforcement is disabled by configuration.");
            return;
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            String product = meta.getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("postgresql")) {
                log.info("Skipping audit immutability trigger setup for non-PostgreSQL database: {}", product);
                return;
            }
        }

        jdbcTemplate.execute("""
                create or replace function prevent_audit_log_mutation()
                returns trigger
                language plpgsql
                as $$
                begin
                  raise exception 'audit_logs is immutable: % operation is not allowed', TG_OP
                    using errcode = '55000';
                end;
                $$;
                """);

        jdbcTemplate.execute("drop trigger if exists trg_audit_logs_immutable on audit_logs");

        AuditIntegrityResponse integrity = auditLogService.verifyIntegrity();
        if (!integrity.isValid()) {
            log.warn("Audit integrity invalid during bootstrap (event {}). Resealing chain before immutability lock.",
                    integrity.getFirstBrokenEventId());
            auditLogService.resealChainForBootstrap();
        }

        jdbcTemplate.execute("""
                create trigger trg_audit_logs_immutable
                before update or delete on audit_logs
                for each row execute function prevent_audit_log_mutation()
                """);

        log.info("Audit immutability trigger enforced on audit_logs table.");
    }
}
