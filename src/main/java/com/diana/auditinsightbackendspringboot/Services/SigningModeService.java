package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Models.Evidence;
import com.diana.auditinsightbackendspringboot.Models.EvidenceDocumentVersion;
import org.springframework.stereotype.Service;

/**
 * Decides how a document is signed:
 * <ul>
 *   <li><b>on the page</b> — PDFs and images always; Word/Excel when a converter is configured, using their stored
 *       PDF conversion;</li>
 *   <li><b>signature certificate</b> — Word/Excel when no converter is configured.</li>
 * </ul>
 * Signed versions record which kind they are. When a Word/Excel document moves from certificates to on-page
 * signing (a converter is added), its earlier certificates stay in the history but are never built upon.
 */
@Service
public class SigningModeService {

    private final DocumentConversionService conversionService;

    public SigningModeService(DocumentConversionService conversionService) {
        this.conversionService = conversionService;
    }

    /** Whether documents of this type are signed on the page (true) or with a signature certificate (false). */
    public boolean placeableType(String fileType) {
        return SignedDocumentService.isStampable(fileType)
                || (DocumentConversionService.isOfficeType(fileType) && conversionService.isEnabled());
    }

    public boolean isPlaceable(Evidence ev) {
        return placeableType(ev.getFileType());
    }

    /** The kind of stored version this document produces now. */
    public String renditionFor(Evidence ev) {
        return isPlaceable(ev) ? EvidenceDocumentVersion.RENDITION_DOCUMENT : EvidenceDocumentVersion.RENDITION_CERTIFICATE;
    }
}
