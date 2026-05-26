package com.diana.auditinsightbackendspringboot.Models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
public class ClientProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    private String firstName;
    private String lastName;
    private String emailAddress;
    private String phone;
    private String address;
    private String companyName;
}
