package com.uasz.alumni.joboffer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Client réel (proxy {@code @Retryable} compris) contre un faux alumni-student-company-service (ALUM-174). */
class StudentDirectoryTest {

    private static final UUID PROFILE = UUID.fromString("0b8a3c1e-5d2f-4a7e-9c11-3f6d8e2a4b90");
    private static final UUID USER = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    private static final String RECRUITER_TOKEN = "Bearer jeton-du-recruteur";

    private HttpServer server;
    private final ConcurrentLinkedQueue<Integer> replies = new ConcurrentLinkedQueue<>();
    private final List<String[]> received = new CopyOnWriteArrayList<>(); // {chemin, Authorization}
    private volatile int last = 200;

    @Configuration
    @EnableResilientMethods
    static class ResilienceOnly {
    }

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/students", exchange -> {
            received.add(new String[] {exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization")});
            Integer next = replies.poll();
            int status = next == null ? last : next;
            last = status;
            String json = status == 200
                    ? "{\"id\":\"" + PROFILE + "\",\"userId\":\"" + USER + "\",\"firstName\":\"Awa\",\"lastName\":\"Ndiaye\"}"
                    : "{\"code\":\"X\",\"message\":\"x\"}";
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", RECRUITER_TOKEN);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void stop() {
        RequestContextHolder.resetRequestAttributes();
        server.stop(0);
    }

    private void withClient(Consumer<StudentDirectory> test) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(ApplicationConversionService.getSharedInstance()))
                .withUserConfiguration(ResilienceOnly.class, StudentDirectory.class)
                .withPropertyValues(
                        "company.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "company.connect-timeout=1s",
                        "company.read-timeout=1s",
                        "company.retry.max-retries=1",
                        "company.retry.delay-ms=10",
                        "company.retry.multiplier=2",
                        "company.retry.max-delay-ms=50")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    test.accept(context.getBean(StudentDirectory.class));
                });
    }

    @Test
    void litLEtudiantAvecLeJetonDuRecruteurEtRenvoieSonIdUtilisateur() {
        withClient(directory -> assertThat(directory.find(PROFILE))
                .contains(new StudentDirectory.StudentRef(USER, "Awa Ndiaye")));

        assertThat(received).hasSize(1);
        assertThat(received.getFirst()[0]).isEqualTo("/v1/students/" + PROFILE);
        assertThat(received.getFirst()[1]).isEqualTo(RECRUITER_TOKEN);
    }

    @Test
    void unEtudiantInconnuDonneUnResultatVideSansRetry() {
        replies.add(404);

        withClient(directory -> assertThat(directory.find(PROFILE)).isEmpty());

        assertThat(received).hasSize(1);
    }

    @Test
    void unSeulRetrySurErreurTransitoireEnsuiteLErreurRemonte() {
        replies.add(503);

        withClient(directory -> assertThatThrownBy(() -> directory.find(PROFILE))
                .isInstanceOf(HttpServerErrorException.ServiceUnavailable.class));

        assertThat(received).hasSize(2);
    }
}
