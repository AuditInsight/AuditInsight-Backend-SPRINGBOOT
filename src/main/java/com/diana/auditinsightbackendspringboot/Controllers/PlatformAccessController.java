package com.diana.auditinsightbackendspringboot.Controllers;

import com.diana.auditinsightbackendspringboot.Services.PlatformFreeAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/platform")
public class PlatformAccessController {

    private final PlatformFreeAccessService freeAccessService;

    public PlatformAccessController(PlatformFreeAccessService freeAccessService) {
        this.freeAccessService = freeAccessService;
    }

    @GetMapping("/access")
    public Mono<Map<String, Boolean>> getAccessStatus() {
        return freeAccessService.isEnabled()
                .map(enabled -> Map.of("freeAccessEnabled", enabled));
    }
}
