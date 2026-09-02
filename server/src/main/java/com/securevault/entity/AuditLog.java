package com.securevault.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One row per security-relevant action (UPLOAD / DOWNLOAD / DELETE).
 * Useful for the demo/report to show accountability.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "file_id")
    private Long fileId;

    @Column(length = 20)
    private String action; // UPLOAD, DOWNLOAD, DELETE

    /** Denormalised so the log stays readable even after a file is deleted. */
    @Column(length = 255)
    private String detail;

    @Column(name = "action_time", nullable = false, updatable = false)
    private Instant actionTime = Instant.now();

    public AuditLog() {
    }

    public AuditLog(Long userId, Long fileId, String action, String detail) {
        this.userId = userId;
        this.fileId = fileId;
        this.action = action;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getFileId() {
        return fileId;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getActionTime() {
        return actionTime;
    }
}
