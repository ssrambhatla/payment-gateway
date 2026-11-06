package com.gateway.api;

import java.io.IOException;
import java.util.UUID;

import com.gateway.domain.Merchant;
import com.gateway.service.MerchantService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API key authentication for payment endpoints. Validates the X-Api-Key
 * header against registered merchants; on success the merchant id is set
 * as a request attribute for controllers. Merchant onboarding stays open.
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String MERCHANT_ID_ATTRIBUTE = "merchantId";

    private final MerchantService merchants;

    public ApiKeyAuthFilter(MerchantService merchants) {
        this.merchants = merchants;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // getServletPath() is empty when the dispatcher servlet is mapped to "/",
        // so match on the request URI instead.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/v1/merchants") || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-Api-Key");
        UUID merchantId = merchants.findByApiKey(apiKey)
                .map(Merchant::getId)
                .orElse(null);
        if (merchantId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"unauthorized\",\"message\":\"Missing or invalid X-Api-Key header\"}");
            return;
        }
        request.setAttribute(MERCHANT_ID_ATTRIBUTE, merchantId);
        chain.doFilter(request, response);
    }
}
