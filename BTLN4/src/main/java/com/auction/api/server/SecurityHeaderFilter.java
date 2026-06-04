package com.auction.api.server;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Adds HTTP security headers to all responses.
 *
 * Render already handles TLS termination and HTTP→HTTPS redirects at the
 * load-balancer level for *.onrender.com — so there is no need to do TLS
 * ourselves. This filter simply adds the response headers that further harden
 * the connection on the client side (browser/Java client enforcement):
 *
 *  • HSTS   – instructs clients to always use HTTPS for this origin
 *  • X-Content-Type-Options – prevents MIME-sniffing attacks
 *  • X-Frame-Options        – prevents clickjacking
 *  • Referrer-Policy        – limits information leakage via Referer header
 *  • Permissions-Policy     – disables unneeded browser features
 *
 * CPU overhead: zero — all values are compile-time constants.
 */
public class SecurityHeaderFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityHeaderFilter.class);

    // 1 year HSTS. Tells clients: "never connect to this origin over plain HTTP".
    // NOTE: Cannot use 'preload' on *.onrender.com because the parent domain
    // is not ours. That's fine — Render's redirect already handles first-hit HTTP.
    private static final String HSTS = "max-age=31536000";

    public void register(Javalin app) {
        app.before(ctx -> {
            String proto = ctx.header("X-Forwarded-Proto");

            // Add HSTS only on HTTPS connections (Render sets X-Forwarded-Proto)
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
