package com.diana.auditinsightbackendspringboot.DTOs;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.net.InetSocketAddress;

/**
 * Where a request came from, for the audit trail. With server.forward-headers-strategy=framework (set in
 * production) the remote address is already the client's address from X-Forwarded-For, not the proxy's.
 */
public record RequestMeta(String ip, String userAgent) {

    public static final RequestMeta NONE = new RequestMeta(null, null);

    public static RequestMeta from(ServerHttpRequest request) {
        if (request == null) return NONE;
        InetSocketAddress remote = request.getRemoteAddress();
        String ip = remote == null ? null
                : remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
        return new RequestMeta(truncate(ip, 64), truncate(request.getHeaders().getFirst(HttpHeaders.USER_AGENT), 512));
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
