package com.oznwoo.gopssl.global.exception;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oznwoo.gopssl.global.config.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * v1(FastAPI) 에러 계약. 기대값은 v1과 같은 FastAPI 0.162로 만든 최소 앱의 실제 응답에서 가져왔다.
 * 아직 실제 API가 없어 테스트 전용 컨트롤러로 확인한다.
 */
@WebMvcTest(ErrorResponseContractTest.ProbeController.class)
@Import({SecurityConfig.class, ErrorResponseContractTest.ProbeController.class})
class ErrorResponseContractTest {

    private static final RequestPostProcessor LOGGED_IN = user("tester");

    @Autowired
    MockMvc mockMvc;

    record Line(@NotNull Integer price) {
    }

    record ProbeBody(@NotBlank String name, @NotNull @Min(0) Integer age, List<@Valid Line> lines) {
    }

    @RestController
    static class ProbeController {

        @PostMapping("/probe/items")
        void create(@Valid @RequestBody ProbeBody body) {
        }

        @GetMapping("/probe/items/{id}")
        void read(@PathVariable Integer id, @RequestParam(defaultValue = "0") Integer q) {
        }

        @GetMapping("/probe/need")
        void need(@RequestParam String q) {
        }

        @GetMapping("/probe/limit")
        void limit(@RequestParam @Min(1) Integer size) {
        }

        @GetMapping("/probe/boom")
        void boom() {
            throw new IllegalStateException("내부 정보");
        }

        @GetMapping("/probe/reason")
        void reason() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다.");
        }
    }

    // 인증 없음

    @Test
    void missingTokenIs401WithBearerChallenge() throws Exception {
        mockMvc.perform(get("/probe/need"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"detail\":\"Not authenticated\"}", JsonCompareMode.STRICT));
    }

    // 요청 검증 실패

    @Test
    void missingFieldIs422WithDetailArray() throws Exception {
        postBody("{\"age\":1}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", hasSize(1)))
                .andExpect(jsonPath("$.detail[0].type").value("missing"))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("name"))
                .andExpect(jsonPath("$.detail[0].msg").isNotEmpty())
                .andExpect(jsonPath("$.detail[0].input", nullValue()));
    }

    @Test
    void invalidValueEchoesInputLikeV1() throws Exception {
        postBody("{\"name\":\"a\",\"age\":-1}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].type").value("value_error"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("age"))
                .andExpect(jsonPath("$.detail[0].input").value(-1));
    }

    @Test
    void multipleErrorsComeInFixedOrder() throws Exception {
        postBody("{}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", hasSize(2)))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("age"))
                .andExpect(jsonPath("$.detail[1].loc[1]").value("name"));
    }

    @Test
    void nestedFieldLocSplitsIndexAsInteger() throws Exception {
        postBody("{\"name\":\"a\",\"age\":1,\"lines\":[{}]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].loc[1]").value("lines"))
                .andExpect(jsonPath("$.detail[0].loc[2]").value(0))
                .andExpect(jsonPath("$.detail[0].loc[3]").value("price"));
    }

    @Test
    void wrongJsonTypeIs422WithFieldLocAndNoInternals() throws Exception {
        postBody("{\"name\":\"a\",\"age\":\"x\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].type").value("type_error"))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("age"))
                .andExpect(jsonPath("$.detail[0].msg").value("Invalid value"));
    }

    @Test
    void wrongJsonTypeInNestedArrayKeepsIndexInLoc() throws Exception {
        postBody("{\"name\":\"a\",\"age\":1,\"lines\":[{\"price\":\"x\"}]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].loc[1]").value("lines"))
                .andExpect(jsonPath("$.detail[0].loc[2]").value(0))
                .andExpect(jsonPath("$.detail[0].loc[3]").value("price"));
    }

    // JSON 파싱 실패

    @Test
    void malformedJsonIs422WithJsonInvalid() throws Exception {
        postBody("{\"name\": ")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].type").value("json_invalid"))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"))
                .andExpect(jsonPath("$.detail[0].loc[1]").isNumber())
                .andExpect(jsonPath("$.detail[0].msg").value("JSON decode error"));
    }

    @Test
    void emptyBodyIs422Missing() throws Exception {
        postBody("")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].type").value("missing"))
                .andExpect(jsonPath("$.detail[0].loc", hasSize(1)))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"));
    }

    @Test
    void nonObjectBodyIs422AtBody() throws Exception {
        postBody("[1]")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].loc", hasSize(1)))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("body"));
    }

    // 경로·쿼리 값 (v1은 모두 422)

    @Test
    void wrongPathVariableTypeIs422() throws Exception {
        mockMvc.perform(get("/probe/items/abc").with(LOGGED_IN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].loc[0]").value("path"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("id"))
                .andExpect(jsonPath("$.detail[0].input").value("abc"));
    }

    @Test
    void wrongQueryTypeIs422() throws Exception {
        mockMvc.perform(get("/probe/items/1").param("q", "abc").with(LOGGED_IN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].loc[0]").value("query"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("q"));
    }

    @Test
    void missingQueryIs422() throws Exception {
        mockMvc.perform(get("/probe/need").with(LOGGED_IN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].type").value("missing"))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("query"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("q"));
    }

    @Test
    void constraintOnQueryIs422() throws Exception {
        mockMvc.perform(get("/probe/limit").param("size", "0").with(LOGGED_IN))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail[0].type").value("value_error"))
                .andExpect(jsonPath("$.detail[0].loc[0]").value("query"))
                .andExpect(jsonPath("$.detail[0].loc[1]").value("size"))
                .andExpect(jsonPath("$.detail[0].input").value(0));
    }

    // 없는 경로

    @Test
    void unknownPathIs404NotFoundWhenLoggedIn() throws Exception {
        mockMvc.perform(get("/probe/nope").with(LOGGED_IN))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"detail\":\"Not Found\"}", JsonCompareMode.STRICT));
    }

    /** v1과 다른 점을 기록한다: v1은 인증 없이도 404다. 대응은 PR 설명의 질문 참고. */
    @Test
    void unknownPathIs401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/probe/nope"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Not authenticated"));
    }

    @Test
    void explicitReasonIsKept() throws Exception {
        mockMvc.perform(get("/probe/reason").with(LOGGED_IN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("고객을 찾을 수 없습니다."));
    }

    // 허용 안 된 메서드

    @Test
    void wrongMethodIs405MethodNotAllowedWithAllowHeader() throws Exception {
        mockMvc.perform(delete("/probe/need").with(LOGGED_IN))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(content().json("{\"detail\":\"Method Not Allowed\"}", JsonCompareMode.STRICT));
    }

    /** v1과 다른 점을 기록한다: v1은 422다. 티켓 표 밖이라 맞추지 않았다. */
    @Test
    void wrongContentTypeIs415WithStatusPhrase() throws Exception {
        mockMvc.perform(post("/probe/items")
                        .with(LOGGED_IN)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{\"name\":\"a\",\"age\":1}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().json("{\"detail\":\"Unsupported Media Type\"}", JsonCompareMode.STRICT));
    }

    // 처리 안 된 예외

    /** v1은 텍스트 응답이다. JSON을 유지하기로 했고 PR 설명에 질문으로 남겼다. */
    @Test
    void unexpectedExceptionIs500JsonWithoutInternals() throws Exception {
        mockMvc.perform(get("/probe/boom").with(LOGGED_IN))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"detail\":\"Internal Server Error\"}", JsonCompareMode.STRICT));
    }

    // 경로 끝 슬래시 (구현하지 않고 현재 동작만 기록한다. v1은 307 리다이렉트)

    @Test
    void trailingSlashIsNotRedirectedAndIs404WhenLoggedIn() throws Exception {
        mockMvc.perform(get("/probe/need/").param("q", "a").with(LOGGED_IN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Not Found"));
    }

    @Test
    void trailingSlashIs401WhenAnonymous() throws Exception {
        mockMvc.perform(get("/probe/need/").param("q", "a")).andExpect(status().isUnauthorized());
    }

    private ResultActions postBody(String json) throws Exception {
        return mockMvc.perform(post("/probe/items")
                .with(LOGGED_IN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
