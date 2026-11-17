package com.uasz.alumni.alumnistudentcompany.controller;

import com.uasz.alumni.alumnistudentcompany.exceptions.NotImplementedYetException;
import com.uasz.alumni.alumnistudentcompany.spi.api.PromotionsApi;
import com.uasz.alumni.alumnistudentcompany.spi.model.Promotion;
import com.uasz.alumni.alumnistudentcompany.spi.model.PromotionCreateRequest;
import com.uasz.alumni.alumnistudentcompany.spi.model.StudentProfile;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromotionsController implements PromotionsApi {

    @Override
    public ResponseEntity<List<Promotion>> listPromotions() {
        // TODO : lister les promotions
        throw new NotImplementedYetException("listPromotions");
    }

    @Override
    public ResponseEntity<Promotion> createPromotion(PromotionCreateRequest promotionCreateRequest) {
        // TODO : créer une promotion -> 201 / 400
        throw new NotImplementedYetException("createPromotion");
    }

    @Override
    public ResponseEntity<List<StudentProfile>> getPromotionGraduates(UUID id) {
        // TODO : diplômés de la promotion -> 200 / 404
        throw new NotImplementedYetException("getPromotionGraduates");
    }
}
