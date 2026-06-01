package com.diana.auditinsightbackendspringboot.DTOs;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateClientProfileRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String address;
}
