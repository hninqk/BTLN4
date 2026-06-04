package com.auction.api.server;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityHeaderFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeaderFilter.class);

    private static final String HSTS = "max-age=31536000";

    public void register(Javalin app) {
        app.before(ctx -> {
            String proto = ctx.header("X-Forwarded-Proto");

            if ("https".equals(proto)) {
                ctx.header("Strict-Transport-Security", HSTS);
            }

            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("Referrer-Policy", "strict-origin-when-cross-origin");
            ctx.header("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
        });
    }
}
