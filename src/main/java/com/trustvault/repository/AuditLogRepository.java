package com.trustvault.repository;

import com.trustvault.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByTimestampDesc();

    Page<AuditLog> findAllByOrderByTimestampDesc(Pageable pageable);

    List<AuditLog> findByActorUsernameOrderByTimestampDesc(String actorUsername);

    List<AuditLog> findByStatusOrderByTimestampDesc(String status);

    long countByStatus(String status);

    long countByAction(String action);
}
