package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAuditorProfileRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String certificationNumber;
}
