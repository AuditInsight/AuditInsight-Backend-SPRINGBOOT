package com.diana.auditinsightbackendspringboot.Models;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Where a signature is stamped on a document: a box on one page, as fractions (0..1) of the displayed page
 * (rotation applied), measured from the top-left corner.
 */
@Table("evidence_signature_placements")
@Getter
@Setter
public class EvidenceSignaturePlacement {

    @Id
    @Column("id")
    private UUID id;

    @Column("signature_id")
    private UUID signatureId;

    @Column("page_index")
    private Integer pageIndex;

    @Column("x")
    private BigDecimal x;

    @Column("y")
    private BigDecimal y;

    @Column("width")
    private BigDecimal width;

    @Column("height")
    private BigDecimal height;

    @Column("created_at")
    private LocalDateTime createdAt;
}
