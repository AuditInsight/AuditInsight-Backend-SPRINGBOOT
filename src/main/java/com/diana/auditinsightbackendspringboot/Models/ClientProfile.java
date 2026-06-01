package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("client_profile")
@Getter
@Setter
public class ClientProfile {

    @Id
    private UUID id;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("email_address")
    private String emailAddress;

    private String phone;

    private String address;

}
