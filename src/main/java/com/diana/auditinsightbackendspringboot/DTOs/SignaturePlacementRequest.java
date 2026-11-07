package com.diana.auditinsightbackendspringboot.DTOs;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * One box where the signature goes: fractions (0..1) of the page as displayed (rotation applied), from the
 * top-left corner. Fractions keep the position correct at any zoom level or screen size.
 */
@Getter
@Setter
public class SignaturePlacementRequest {
    @NotNull @Min(value = 0, message = "pageIndex must be 0 or more")
    private Integer pageIndex;

    @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
    private Double x;

    @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
    private Double y;

    @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
    private Double width;

    @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("1.0")
    private Double height;
}
