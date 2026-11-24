package com.uasz.alumni.notificationmessaging.controller;

import com.uasz.alumni.notificationmessaging.security.CurrentUser;
import com.uasz.alumni.notificationmessaging.service.MessageAttachments;
import com.uasz.alumni.notificationmessaging.spi.api.MessageAttachmentsApi;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentSettings;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadRequest;
import com.uasz.alumni.notificationmessaging.spi.model.AttachmentUploadTicket;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Pièces jointes des messages (ALUM-261) : réglages et autorisation d'envoi direct vers le stockage. */
@RestController
public class MessageAttachmentsController implements MessageAttachmentsApi {

    private final MessageAttachments attachments;

    public MessageAttachmentsController(MessageAttachments attachments) {
        this.attachments = attachments;
    }

    @Override
    public ResponseEntity<AttachmentSettings> getAttachmentSettings() {
        return ResponseEntity.ok(attachments.settings());
    }

    @Override
    public ResponseEntity<AttachmentUploadTicket> createAttachmentUpload(AttachmentUploadRequest request) {
        return ResponseEntity.ok(attachments.ticket(request, CurrentUser.get().id()));
    }
}
