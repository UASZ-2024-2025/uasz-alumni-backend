package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.service.PromotionService;
import com.uasz.alumni.alumnistudentcompany.spi.api.PromotionsApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.Promotion;
import com.uasz.alumni.alumnistudentcompany.spi.model.PromotionCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

/** Promotions académiques (ALUM-88) : routes du contrat, création réservée aux administrateurs. */
@RestController
public class PromotionsController implements PromotionsApi {

    private final PromotionService service;

    public PromotionsController(PromotionService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<List<Promotion>> listPromotions() {
        return ResponseEntity.ok(service.list());
    }

    @Override
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<Promotion> createPromotion(PromotionCreateRequest promotionCreateRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(promotionCreateRequest));
    }

    @Override
    public ResponseEntity<List<StudentProfile>> getPromotionGraduates(UUID id) {
        return ResponseEntity.ok(service.graduates(id));
    }
}
