package com.diana.auditinsightbackendspringboot.Services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Converts plan USD prices into a payment provider's charge currency using ExchangeRate-API.com.
 * Rates are cached for {@link #CACHE_TTL} — a failed refresh call always errors out rather than
 * falling back to a guessed or stale rate.
 */
@Service
@Slf4j
public class ExchangeRateService {

    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${exchange.rate.api.key}")
    private String apiKey;

    @Value("${exchange.rate.api.base-url}")
    private String baseUrl;

    private final AtomicReference<CachedRates> cache = new AtomicReference<>();

    public record ConversionResult(BigDecimal usdAmount, String currency,
                                    BigDecimal convertedAmount, BigDecimal rate) {}

    private record CachedRates(Map<String, BigDecimal> rates, Instant fetchedAt) {}

    public Mono<ConversionResult> convert(BigDecimal usdAmount, String targetCurrency) {
        String currency = targetCurrency.toUpperCase();
        return getRates()
                .map(rates -> {
                    BigDecimal rate = rates.get(currency);
                    if (rate == null) {
                        throw new RuntimeException("Unsupported currency: " + currency);
                    }
                    BigDecimal converted = round(usdAmount.multiply(rate), currency);
                    return new ConversionResult(usdAmount, currency, converted, rate);
                });
    }

    private BigDecimal round(BigDecimal amount, String currency) {
        int scale;
        try {
            scale = Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException e) {
            scale = 2;
        }
        if (scale < 0) {
            scale = 2;
        }
        return amount.setScale(scale, RoundingMode.HALF_UP);
    }

    private Mono<Map<String, BigDecimal>> getRates() {
        CachedRates cached = cache.get();
        if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return Mono.just(cached.rates());
        }
        return Mono.fromCallable(this::fetchLiveRates)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(rates -> cache.set(new CachedRates(rates, Instant.now())));
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> fetchLiveRates() {
        String url = baseUrl + "/" + apiKey + "/latest/USD";
        ResponseEntity<Map> response;
        try {
            response = restTemplate.getForEntity(url, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reach exchange rate API: " + e.getMessage(), e);
        }

        Map<String, Object> body = response.getBody();
        if (!response.getStatusCode().is2xxSuccessful() || body == null) {
            throw new RuntimeException("Exchange rate API returned HTTP " + response.getStatusCode());
        }
        if (!"success".equals(body.get("result"))) {
            throw new RuntimeException("Exchange rate API returned an error: " + body.get("error-type"));
        }

        Map<String, Object> rawRates = (Map<String, Object>) body.get("conversion_rates");
        if (rawRates == null) {
            throw new RuntimeException("Exchange rate API response missing conversion_rates");
        }

        return rawRates.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new BigDecimal(e.getValue().toString())));
    }
}
