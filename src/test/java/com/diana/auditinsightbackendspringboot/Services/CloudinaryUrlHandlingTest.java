package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryUrlHandlingTest {

    @Test
    void permanentDeliverySignatureIsDropped() {
        // Cloudinary returns private URLs with a permanent signature that would let anyone holding them open the file.
        assertEquals("https://res.cloudinary.com/demo/image/authenticated/v1794052227/auditinsight/x.png",
                CloudinaryService.withoutDeliverySignature(
                        "https://res.cloudinary.com/demo/image/authenticated/s--Fv9peHzz--/v1794052227/auditinsight/x.png"));
    }

    @Test
    void urlsWithoutSignatureAreUnchanged() {
        String url = "https://res.cloudinary.com/demo/raw/upload/v1/auditinsight/x.pdf";
        assertEquals(url, CloudinaryService.withoutDeliverySignature(url));
    }

    @Test
    void privateUrlsAreRecognised() {
        assertTrue(CloudinaryService.isPrivateUrl("https://res.cloudinary.com/demo/raw/authenticated/v1/a.pdf"));
        assertTrue(CloudinaryService.isPrivateUrl("https://res.cloudinary.com/demo/image/private/v1/a.png"));
        assertFalse(CloudinaryService.isPrivateUrl("https://res.cloudinary.com/demo/image/upload/v1/a.png"));
        assertFalse(CloudinaryService.isPrivateUrl(null));
    }
}
