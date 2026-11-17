package com.uasz.alumni.user.service;

import com.uasz.alumni.user.model.AuditLog;
import com.uasz.alumni.user.repository.AuditLogRepository;
import com.uasz.alumni.user.utils.ClientIp;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Journal d'audit (table audit_logs + log applicatif). Participe à la transaction appelante. */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger("AUDIT");

    private final AuditLogRepository repo;

    public AuditService(AuditLogRepository repo) {
        this.repo = repo;
    }

    public void record(String action, UUID actorId, UUID targetId, String details) {
        String safeDetails = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        String ip = ClientIp.current();
        repo.save(new AuditLog(actorId, action, targetId, safeDetails, ip));
        log.info("action={} actor={} target={} ip={}", action, actorId, targetId, ip);
    }
}
