package com.securevault.web;

import com.securevault.entity.User;
import com.securevault.service.AuditService;
import com.securevault.service.UserService;
import com.securevault.web.dto.FileDtos.AuditItem;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService audit;
    private final UserService userService;

    public AuditController(AuditService audit, UserService userService) {
        this.audit = audit;
        this.userService = userService;
    }

    @GetMapping
    public List<AuditItem> myAudit(Authentication auth) {
        User user = userService.requireByUsername(auth.getName());
        return audit.forUser(user.getId()).stream().map(AuditItem::from).toList();
    }
}
