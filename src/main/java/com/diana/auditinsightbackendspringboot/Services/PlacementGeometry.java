package com.diana.auditinsightbackendspringboot.Services;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.util.Matrix;

/**
 * Converts signature placements from "display space" — the page as a viewer shows it (its /Rotate applied),
 * in points, origin at the top-left, y growing downwards — into PDF user space (origin bottom-left, unrotated).
 * <p>
 * The browser viewer (pdf.js) renders the page's crop box with its rotation, and the signer positions the box on
 * that picture; storing fractions of the displayed size makes the position independent of zoom and screen size.
 */
public final class PlacementGeometry {

    public record Point(float x, float y) {}

    private PlacementGeometry() {}

    /** /Rotate normalised to 0, 90, 180 or 270. */
    public static int normalize(int rotation) {
        int r = ((rotation % 360) + 360) % 360;
        return (r / 90) * 90;
    }

    public static float displayWidth(PDRectangle box, int rotation) {
        int r = normalize(rotation);
        return r == 90 || r == 270 ? box.getHeight() : box.getWidth();
    }

    public static float displayHeight(PDRectangle box, int rotation) {
        int r = normalize(rotation);
        return r == 90 || r == 270 ? box.getWidth() : box.getHeight();
    }

    /** Maps a display-space point (points from the displayed page's top-left) to PDF user space. */
    public static Point toUser(PDRectangle box, int rotation, float dx, float dy) {
        float llx = box.getLowerLeftX(), lly = box.getLowerLeftY();
        float urx = box.getUpperRightX(), ury = box.getUpperRightY();
        return switch (normalize(rotation)) {
            case 90 -> new Point(llx + dy, lly + dx);   // displayed clockwise: top-left is the page's bottom-left
            case 180 -> new Point(urx - dx, lly + dy);
            case 270 -> new Point(urx - dy, ury - dx);
            default -> new Point(llx + dx, ury - dy);
        };
    }

    /**
     * Matrix that maps an image's unit square (x right, y up) onto the display-space rectangle
     * (dx, dy, dw, dh), so the image appears upright to the viewer whatever the page rotation.
     */
    public static Matrix boxMatrix(PDRectangle box, int rotation, float dx, float dy, float dw, float dh) {
        Point origin = toUser(box, rotation, dx, dy + dh);   // image bottom-left
        Point right = toUser(box, rotation, dx + dw, dy + dh);
        Point up = toUser(box, rotation, dx, dy);
        return new Matrix(right.x() - origin.x(), right.y() - origin.y(),
                up.x() - origin.x(), up.y() - origin.y(),
                origin.x(), origin.y());
    }

    /**
     * Text matrix with its origin at the display-space point (the text baseline start), x pointing right and y
     * pointing up as the viewer sees them, at unit scale (the font size scales it).
     */
    public static Matrix textMatrix(PDRectangle box, int rotation, float dx, float dy) {
        Point origin = toUser(box, rotation, dx, dy);
        Point right = toUser(box, rotation, dx + 1, dy);
        Point up = toUser(box, rotation, dx, dy - 1);
        return new Matrix(right.x() - origin.x(), right.y() - origin.y(),
                up.x() - origin.x(), up.y() - origin.y(),
                origin.x(), origin.y());
    }
}
