package com.uasz.alumni.alumnistudentcompany.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationClientTest {

    @Test
    void posteLaNotificationAvecLeJetonDeLAppelant() throws Exception {
        var auth = new AtomicReference<String>();
        var corps = new AtomicReference<String>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        srv.createContext("/v1/notifications/send", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            corps.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        srv.start();
        try {
            var client = new NotificationClient("http://127.0.0.1:" + srv.getAddress().getPort() + "/v1");
            UUID etudiant = UUID.randomUUID();
            client.notifier(etudiant, "Titre", "Contenu", UUID.randomUUID(), "jeton");
            assertEquals("Bearer jeton", auth.get());
            assertTrue(corps.get().contains(etudiant.toString()));
            assertTrue(corps.get().contains("SYSTEM_ALERT"));
        } finally {
            srv.stop(0);
        }
    }

    @Test
    void unServiceInjoignableNeLeveAucuneException() {
        var client = new NotificationClient("http://127.0.0.1:1/v1");
        assertDoesNotThrow(() -> client.notifier(UUID.randomUUID(), "t", "c", UUID.randomUUID(), "j"));
    }
}
