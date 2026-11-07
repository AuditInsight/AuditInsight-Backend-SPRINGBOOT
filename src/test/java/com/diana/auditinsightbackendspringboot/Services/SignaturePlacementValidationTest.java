package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.DTOs.SignaturePlacementRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SignaturePlacementValidationTest {

    private static final String IMAGE = "data:image/png;base64,AAAA";

    @Test
    void noPlacements_isAlwaysFine() {
        assertNull(SignatureService.validatePlacements(List.of(), null, null));
    }

    @Test
    void boxInsideThePage_isAccepted() {
        assertNull(SignatureService.validatePlacements(List.of(box(0, 0.6, 0.85, 0.3, 0.1)), null, IMAGE));
    }

    @Test
    void boxTouchingTheEdge_isAccepted() {
        assertNull(SignatureService.validatePlacements(List.of(box(2, 0.7, 0.9, 0.3, 0.1)), "Jane", null));
    }

    @Test
    void boxSpillingOffTheRightEdge_isRejected() {
        assertEquals("A signature position must lie within the page",
                SignatureService.validatePlacements(List.of(box(0, 0.8, 0.1, 0.3, 0.1)), "Jane", null));
    }

    @Test
    void boxSpillingOffTheBottom_isRejected() {
        assertEquals("A signature position must lie within the page",
                SignatureService.validatePlacements(List.of(box(0, 0.1, 0.95, 0.3, 0.1)), "Jane", null));
    }

    @Test
    void placementWithoutASignature_isRejected() {
        assertEquals("Draw or type your signature before placing it on the document",
                SignatureService.validatePlacements(List.of(box(0, 0.1, 0.1, 0.3, 0.1)), null, null));
    }

    private static SignaturePlacementRequest box(int page, double x, double y, double w, double h) {
        SignaturePlacementRequest p = new SignaturePlacementRequest();
        p.setPageIndex(page);
        p.setX(x);
        p.setY(y);
        p.setWidth(w);
        p.setHeight(h);
        return p;
    }
}
