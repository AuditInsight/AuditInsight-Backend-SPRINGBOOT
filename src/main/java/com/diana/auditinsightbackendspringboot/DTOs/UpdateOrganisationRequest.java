package com.diana.auditinsightbackendspringboot.DTOs;

import com.diana.auditinsightbackendspringboot.Enum.OrganisationType;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UpdateOrganisationRequest {

    private String name;

    private OrganisationType industry;

    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "fiscalYearStart must be in MM-dd format (e.g. 01-01)")
    private String fiscalYearStart;

    @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$",
             message = "fiscalYearEnd must be in MM-dd format (e.g. 12-31)")
    private String fiscalYearEnd;

    private List<String> currencies;
}
