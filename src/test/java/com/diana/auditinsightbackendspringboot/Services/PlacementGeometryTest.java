package com.diana.auditinsightbackendspringboot.Services;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacementGeometryTest {

    // A crop box that doesn't start at the origin, to catch missing offsets: 600 wide, 800 tall.
    private final PDRectangle box = new PDRectangle(10, 20, 600, 800);
    private static final float EPS = 0.001f;

    @Test
    void normalize_handlesNegativeAndLargeRotations() {
        assertEquals(270, PlacementGeometry.normalize(-90));
        assertEquals(90, PlacementGeometry.normalize(450));
        assertEquals(0, PlacementGeometry.normalize(360));
    }

    @Test
    void displaySize_swapsForQuarterTurns() {
        assertEquals(600, PlacementGeometry.displayWidth(box, 0), EPS);
        assertEquals(800, PlacementGeometry.displayWidth(box, 90), EPS);
        assertEquals(600, PlacementGeometry.displayHeight(box, 270), EPS);
    }

    @Test
    void toUser_unrotated_topLeftIsUpperLeftCorner() {
        assertPoint(10, 820, PlacementGeometry.toUser(box, 0, 0, 0));
        assertPoint(610, 20, PlacementGeometry.toUser(box, 0, 600, 800));
    }

    @Test
    void toUser_rotated90_displayTopLeftIsBottomLeftCorner() {
        // Shown turned clockwise: the page's bottom-left corner appears at the top-left.
        assertPoint(10, 20, PlacementGeometry.toUser(box, 90, 0, 0));
        // Display bottom-right (800 wide, 600 tall) is the page's top-right corner.
        assertPoint(610, 820, PlacementGeometry.toUser(box, 90, 800, 600));
    }

    @Test
    void toUser_rotated180_displayTopLeftIsBottomRightCorner() {
        assertPoint(610, 20, PlacementGeometry.toUser(box, 180, 0, 0));
        assertPoint(10, 820, PlacementGeometry.toUser(box, 180, 600, 800));
    }

    @Test
    void toUser_rotated270_displayTopLeftIsUpperRightCorner() {
        assertPoint(610, 820, PlacementGeometry.toUser(box, 270, 0, 0));
        assertPoint(10, 20, PlacementGeometry.toUser(box, 270, 800, 600));
    }

    @Test
    void boxMatrix_mapsImageCornersOntoTheDisplayRectangle_forEveryRotation() {
        for (int rotation : new int[]{0, 90, 180, 270}) {
            float dx = 50, dy = 70, dw = 120, dh = 40;
            Matrix m = PlacementGeometry.boxMatrix(box, rotation, dx, dy, dw, dh);
            // Image (0,0) = bottom-left as seen, (1,1) = top-right as seen.
            assertPoint(PlacementGeometry.toUser(box, rotation, dx, dy + dh), apply(m, 0, 0));
            assertPoint(PlacementGeometry.toUser(box, rotation, dx + dw, dy + dh), apply(m, 1, 0));
            assertPoint(PlacementGeometry.toUser(box, rotation, dx, dy), apply(m, 0, 1));
            assertPoint(PlacementGeometry.toUser(box, rotation, dx + dw, dy), apply(m, 1, 1));
        }
    }

    @Test
    void textMatrix_pointsRightAndUpAsTheViewerSeesThePage() {
        for (int rotation : new int[]{0, 90, 180, 270}) {
            Matrix m = PlacementGeometry.textMatrix(box, rotation, 100, 200);
            assertPoint(PlacementGeometry.toUser(box, rotation, 100, 200), apply(m, 0, 0));
            assertPoint(PlacementGeometry.toUser(box, rotation, 110, 200), apply(m, 10, 0));
            assertPoint(PlacementGeometry.toUser(box, rotation, 100, 190), apply(m, 0, 10));
        }
    }

    private static PlacementGeometry.Point apply(Matrix m, float x, float y) {
        Point2D.Float p = m.transformPoint(x, y);
        return new PlacementGeometry.Point(p.x, p.y);
    }

    private static void assertPoint(float x, float y, PlacementGeometry.Point actual) {
        assertEquals(x, actual.x(), EPS, "x");
        assertEquals(y, actual.y(), EPS, "y");
    }

    private static void assertPoint(PlacementGeometry.Point expected, PlacementGeometry.Point actual) {
        assertPoint(expected.x(), expected.y(), actual);
    }
}
