package com.uasz.alumni.notificationmessaging.config;

import com.uasz.alumni.notificationmessaging.service.AttachmentStorage;
import com.uasz.alumni.notificationmessaging.service.CloudinaryAttachmentStorage;
import com.uasz.alumni.notificationmessaging.service.DisabledAttachmentStorage;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Stockage des pièces jointes (ALUM-261) : Cloudinary si CLOUDINARY_URL est renseignée, sinon désactivé. */
@Configuration
public class AttachmentStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(AttachmentStorageConfig.class);

    @Bean
    AttachmentStorage attachmentStorage(@Value("${attachments.cloudinary-url:}") String cloudinaryUrl, Clock clock) {
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            log.info("Pièces jointes : désactivées (CLOUDINARY_URL vide)");
            return new DisabledAttachmentStorage();
        }
        CloudinaryAttachmentStorage storage = new CloudinaryAttachmentStorage(cloudinaryUrl.trim(), clock);
        log.info("Pièces jointes : Cloudinary");
        return storage;
    }
}
