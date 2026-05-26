package com.diana.auditinsightbackendspringboot.Models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class AuditorProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false)
    private Long id;

    private String firstName;
    private String lastName;
    private String emailAddress;
    private String phone;
    private String certificationNumber;



}
