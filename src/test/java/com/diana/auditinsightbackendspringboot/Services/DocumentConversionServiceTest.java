package com.diana.auditinsightbackendspringboot.Services;

import com.diana.auditinsightbackendspringboot.Exceptions.Custom.InvalidRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentConversionServiceTest {

    private DisposableServer server;

    @AfterEach
    void stop() {
        if (server != null) server.disposeNow();
    }

    @Test
    void disabledWithoutUrl() {
        DocumentConversionService service = new DocumentConversionService("", 90);

        assertFalse(service.isEnabled());
        StepVerifier.create(service.convertToPdf(new byte[]{1}, "document.docx"))
                .expectError(InvalidRecord.class)
                .verify();
    }

    @Test
    void isOfficeType_coversWordAndExcelOnly() {
        assertTrue(DocumentConversionService.isOfficeType("DOCX"));
        assertTrue(DocumentConversionService.isOfficeType("xls"));
        assertFalse(DocumentConversionService.isOfficeType("pdf"));
        assertFalse(DocumentConversionService.isOfficeType(null));
    }

    @Test
    void convertToPdf_postsTheFileToGotenbergsLibreOfficeRoute() {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create().port(0)
                .route(routes -> routes.post("/forms/libreoffice/convert", (req, res) -> {
                    path.set(req.path());
                    return req.receive().aggregate().asString(StandardCharsets.ISO_8859_1)
                            .flatMap(b -> {
                                body.set(b);
                                return res.header("Content-Type", "application/pdf")
                                        .sendByteArray(Mono.just("%PDF-1.7 converted".getBytes(StandardCharsets.UTF_8)))
                                        .then();
                            });
                }))
                .bindNow();
        DocumentConversionService service =
                new DocumentConversionService("http://localhost:" + server.port() + "/", 10);

        StepVerifier.create(service.convertToPdf("word-bytes".getBytes(StandardCharsets.UTF_8), "document.docx"))
                .assertNext(pdf -> assertEquals("%PDF-1.7 converted", new String(pdf, StandardCharsets.UTF_8)))
                .verifyComplete();

        assertEquals("forms/libreoffice/convert", path.get());
        assertTrue(body.get().contains("name=\"files\""), "multipart field must be named files");
        assertTrue(body.get().contains("filename=\"document.docx\""), "LibreOffice needs the file extension");
        assertTrue(body.get().contains("word-bytes"));
    }

    @Test
    void convertToPdf_converterError_becomesAFriendlyMessage() {
        server = HttpServer.create().port(0)
                .route(routes -> routes.post("/forms/libreoffice/convert",
                        (req, res) -> res.status(503).send()))
                .bindNow();
        DocumentConversionService service = new DocumentConversionService("http://localhost:" + server.port(), 10);

        StepVerifier.create(service.convertToPdf(new byte[]{1}, "document.xlsx"))
                .expectErrorMatches(e -> e instanceof InvalidRecord && e.getMessage().contains("convert"))
                .verify();
    }
}
