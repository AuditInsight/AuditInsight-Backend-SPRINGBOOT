package com.diana.auditinsightbackendspringboot.Services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateService();
        ReflectionTestUtils.setField(exchangeRateService, "restTemplate", restTemplate);
        ReflectionTestUtils.setField(exchangeRateService, "apiKey", "test-key");
        ReflectionTestUtils.setField(exchangeRateService, "baseUrl", "https://v6.exchangerate-api.com/v6");
    }

    private ResponseEntity<Map> successResponse(Map<String, Object> rates) {
        return ResponseEntity.ok(Map.of("result", "success", "conversion_rates", rates));
    }

    @Test
    void convert_zeroDecimalCurrency_roundsToWholeUnits() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(successResponse(Map.of("RWF", 1420.567)));

        StepVerifier.create(exchangeRateService.convert(new BigDecimal("10.00"), "rwf"))
                .expectNextMatches(r -> r.currency().equals("RWF")
                        && r.convertedAmount().compareTo(new BigDecimal("14206")) == 0
                        && r.rate().compareTo(new BigDecimal("1420.567")) == 0)
                .verifyComplete();
    }

    @Test
    void convert_twoDecimalCurrency_roundsToCents() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(successResponse(Map.of("EUR", 0.9231)));

        StepVerifier.create(exchangeRateService.convert(new BigDecimal("10.00"), "EUR"))
                .expectNextMatches(r -> r.currency().equals("EUR")
                        && r.convertedAmount().compareTo(new BigDecimal("9.23")) == 0)
                .verifyComplete();
    }

    @Test
    void convert_secondCallWithinTtl_usesCacheAndDoesNotRefetch() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(successResponse(Map.of("RWF", 1400)));

        StepVerifier.create(exchangeRateService.convert(new BigDecimal("5"), "RWF"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(exchangeRateService.convert(new BigDecimal("7"), "RWF"))
                .expectNextCount(1)
                .verifyComplete();

        verify(restTemplate, times(1))
                .getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class));
    }

    @Test
    void convert_apiCallThrows_propagatesErrorWithoutFallback() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenThrow(new RuntimeException("connection refused"));

        StepVerifier.create(exchangeRateService.convert(new BigDecimal("10"), "RWF"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void convert_apiReturnsErrorResult_propagatesError() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("result", "error", "error-type", "invalid-key")));

        StepVerifier.create(exchangeRateService.convert(new BigDecimal("10"), "RWF"))
                .expectErrorMatches(e -> e.getMessage().contains("invalid-key"))
                .verify();
    }

    @Test
    void convert_unsupportedCurrency_propagatesError() {
        when(restTemplate.getForEntity(anyString(), org.mockito.ArgumentMatchers.eq(Map.class)))
                .thenReturn(successResponse(Map.of("EUR", 0.92)));

        StepVerifier.create(exchangeRateService.convert(new BigDecimal("10"), "XYZ"))
                .expectErrorMatches(e -> e.getMessage().contains("XYZ"))
                .verify();
    }
}
