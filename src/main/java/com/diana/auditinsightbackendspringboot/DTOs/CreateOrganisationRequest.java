package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateOrganisationRequest {

    @NotBlank(message = "the organisation name is required")
    private String name;

    @NotNull(message = "Organisation industry field is requires (Private Organisation or NGO)")
    private OrganisationType industry;

    @NotBlank(message = "provide the starting date for your fiscal year")
    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "fiscalYearStart must be in MM-dd format (e.g. 01-01)")
    private String fiscalYearStart;

    @NotBlank(message = "provide the ending  date for your fiscal year")
    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "fiscalYearEnd must be in MM-dd format (e.g. 12-31)")
    private String fiscalYearEnd;

    @NotEmpty(message = "At least one currency is required")
    private List<String> currencies;

    @NotBlank(message = "provide the company size")
    private String size;

}
