package com.oznwoo.gopssl.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** MockMvc를 거치지 않고 실제 서블릿 컨테이너와 전체 설정으로 인증 없는 요청의 응답을 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ErrorResponseHttpTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void missingTokenIs401WithBearerChallenge() throws Exception {
        HttpResponse<String> response = send("GET", "/customers");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("www-authenticate")).hasValue("Bearer");
        assertThat(response.body()).isEqualTo("{\"detail\":\"Not authenticated\"}");
    }

    @Test
    void wrongMethodOnPublicPathIs405() throws Exception {
        HttpResponse<String> response = send("POST", "/health");

        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("allow")).hasValue("GET");
        assertThat(response.body()).isEqualTo("{\"detail\":\"Method Not Allowed\"}");
    }

    /** v1과 다른 점을 기록한다: v1은 인증 없이도 404, 슬래시가 다르면 307이다. */
    @Test
    void unknownPathAndTrailingSlashAre401WithoutToken() throws Exception {
        assertThat(send("GET", "/nope").statusCode()).isEqualTo(401);
        assertThat(send("GET", "/health/").statusCode()).isEqualTo(401);
    }
}
