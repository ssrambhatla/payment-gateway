package com.gateway.api;

import java.util.UUID;

import com.gateway.domain.Merchant;
import com.gateway.service.MerchantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Merchant onboarding. Open endpoint (no API key yet — this creates one). */
@RestController
@RequestMapping("/v1/merchants")
public class MerchantController {

    private final MerchantService merchants;

    public MerchantController(MerchantService merchants) {
        this.merchants = merchants;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MerchantResponse create(@Valid @RequestBody CreateMerchantRequest request) {
        Merchant merchant = merchants.createMerchant(request.name());
        // The API key is shown exactly once, here.
        return new MerchantResponse(merchant.getId(), merchant.getApiKey());
    }
}
