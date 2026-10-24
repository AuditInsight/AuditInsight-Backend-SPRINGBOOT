package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String username;
    private String fullName;
    private Role role;
    private boolean active;
    private boolean verified;
    private String authProvider;
    private LocalDateTime createdAt;
    private List<AdminUserOrganisationSummary> organisations;
}
