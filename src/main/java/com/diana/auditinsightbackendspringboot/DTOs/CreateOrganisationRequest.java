package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateOrganisationRequest {

    @NotBlank
    private String name;

    private String industry;

    @NotBlank
    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "fiscalYearStart must be in MM-dd format (e.g. 01-01)")
    private String fiscalYearStart;

    @NotBlank
    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "fiscalYearEnd must be in MM-dd format (e.g. 12-31)")
    private String fiscalYearEnd;

    @NotEmpty(message = "At least one currency is required")
    private List<String> currencies;
}
