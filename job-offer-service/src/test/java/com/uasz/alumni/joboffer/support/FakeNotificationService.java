package com.uasz.alumni.joboffer.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Faux notification-messaging-service : enregistre les appels reçus sur POST /v1/notifications/send
 * et répond selon un scénario (codes HTTP successifs, délai de réponse).
 */
public final class FakeNotificationService implements AutoCloseable {

    public record Call(String authorization, String body) {
    }

    private final HttpServer server;
    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private final ConcurrentLinkedQueue<Integer> scriptedStatuses = new ConcurrentLinkedQueue<>();
    private volatile int defaultStatus = 201;
    private volatile Duration delay = Duration.ZERO;

    public FakeNotificationService() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/notifications/send", exchange -> {
            calls.add(new Call(exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Integer status = scriptedStatuses.poll();
            try {
                exchange.sendResponseHeaders(status != null ? status : defaultStatus, -1);
            } catch (IOException ignored) {
                // le client a abandonné (timeout) : normal dans le test de timeout
            }
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /** Réinitialise le scénario : répond {@code defaultStatus}, sans délai. */
    public void reset() {
        calls.clear();
        scriptedStatuses.clear();
        defaultStatus = 201;
        delay = Duration.ZERO;
    }

    /** Les prochaines réponses suivent {@code statuses}, puis {@code defaultStatus}. */
    public void respondWith(int... statuses) {
        for (int s : statuses) {
            scriptedStatuses.add(s);
        }
    }

    public void alwaysRespond(int status) {
        defaultStatus = status;
    }

    public void delayResponses(Duration delay) {
        this.delay = delay;
    }

    public List<Call> calls() {
        return calls;
    }

    /** Attend qu'au moins {@code count} appels soient reçus (les notifications partent en asynchrone). */
    public void awaitCalls(int count, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (calls.size() < count && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        if (calls.size() < count) {
            throw new AssertionError("Attendu " + count + " appel(s), reçu " + calls.size());
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
