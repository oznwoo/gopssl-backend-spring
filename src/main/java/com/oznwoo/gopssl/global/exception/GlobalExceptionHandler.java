package com.oznwoo.gopssl.global.exception;

import com.oznwoo.gopssl.global.exception.ApiValidationError.Item;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;

/**
 * 컨트롤러에서 나가는 모든 에러를 v1(FastAPI)과 같은 형식으로 통일한다.
 * 요청 검증 실패는 422 + {@link ApiValidationError}, 그 밖에는 {@link ApiError}다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final HttpStatus VALIDATION_STATUS = HttpStatus.UNPROCESSABLE_CONTENT;
    private static final String TYPE_MISSING = "missing";
    private static final String TYPE_VALUE_ERROR = "value_error";
    private static final String TYPE_TYPE_ERROR = "type_error";
    private static final String TYPE_JSON_INVALID = "json_invalid";
    private static final String MSG_FIELD_REQUIRED = "Field required";
    private static final String MSG_INVALID_VALUE = "Invalid value";
    private static final String MSG_JSON_INVALID = "JSON decode error";
    private static final String LOC_BODY = "body";
    private static final String LOC_QUERY = "query";
    private static final String LOC_PATH = "path";
    private static final String LOC_HEADER = "header";

    /** {@code lines[0].price}를 {@code lines}, {@code 0}, {@code price}로 나눈다. */
    private static final Pattern FIELD_PATH = Pattern.compile("\\[(\\d+)]|\\[([^]]+)]|([^.\\[]+)");

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Bean Validation의 오류 순서는 고정돼 있지 않은데 프론트는 첫 항목만 보여주므로 순서를 고정한다.
        List<Item> items = ex.getBindingResult().getAllErrors().stream()
                .map(GlobalExceptionHandler::bodyItem)
                .sorted(Comparator.comparing(item -> item.loc().toString()))
                .toList();
        return respondValidation(items, headers);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return respondValidation(List.of(unreadableBodyItem(ex)), headers);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Item> items = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            MethodParameter parameter = result.getMethodParameter();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                items.add(new Item(
                        result.getArgument() == null ? TYPE_MISSING : TYPE_VALUE_ERROR,
                        List.of(sourceOf(parameter), parameterName(parameter)),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : MSG_INVALID_VALUE,
                        result.getArgument()));
            }
        });
        return respondValidation(items, headers);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Item item = new Item(TYPE_MISSING, List.of(LOC_QUERY, ex.getParameterName()), MSG_FIELD_REQUIRED, null);
        return respondValidation(List.of(item), headers);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (!(ex instanceof MethodArgumentTypeMismatchException mismatch)) {
            return super.handleTypeMismatch(ex, headers, status, request);
        }
        Item item = new Item(
                TYPE_TYPE_ERROR,
                List.of(sourceOf(mismatch.getParameter()), mismatch.getName()),
                MSG_INVALID_VALUE,
                mismatch.getValue());
        return respondValidation(List.of(item), headers);
    }

    /**
     * 404·405 등 프레임워크 예외는 v1처럼 상태 문구만 보낸다. Spring 기본 메시지에는 요청 경로와 내부 표현이 들어간다.
     * {@link ResponseStatusException}은 코드에서 직접 적은 사유이므로 그대로 쓴다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex instanceof ResponseStatusException statusException ? statusException.getReason() : null;
        return respond(new ApiError(detail != null ? detail : reasonOf(status)), headers, status);
    }

    /** v1은 텍스트로 응답하지만, 형식을 맞출지 결정될 때까지 JSON을 유지한다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex) {
        log.error("처리되지 않은 예외", ex);
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return respond(new ApiError(status.getReasonPhrase()), new HttpHeaders(), status);
    }

    private static Item bodyItem(ObjectError error) {
        if (!(error instanceof FieldError fieldError)) {
            return new Item(TYPE_VALUE_ERROR, List.of(LOC_BODY), messageOf(error), null);
        }
        List<Object> loc = new ArrayList<>();
        loc.add(LOC_BODY);
        loc.addAll(splitFieldPath(fieldError.getField()));
        // 바인딩 실패의 기본 메시지에는 자바 타입 이름이 들어가므로 내보내지 않는다.
        if (fieldError.isBindingFailure()) {
            return new Item(TYPE_TYPE_ERROR, loc, MSG_INVALID_VALUE, fieldError.getRejectedValue());
        }
        String type = fieldError.getRejectedValue() == null ? TYPE_MISSING : TYPE_VALUE_ERROR;
        return new Item(type, loc, messageOf(error), fieldError.getRejectedValue());
    }

    private static Item unreadableBodyItem(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof StreamReadException parseError) {
            List<Object> loc = new ArrayList<>(List.of(LOC_BODY));
            if (parseError.getLocation() != null) {
                long offset = Math.max(
                        parseError.getLocation().getCharOffset(), parseError.getLocation().getByteOffset());
                if (offset >= 0) {
                    loc.add(offset);
                }
            }
            return new Item(TYPE_JSON_INVALID, loc, MSG_JSON_INVALID, null);
        }
        if (cause instanceof JacksonException mappingError) {
            List<Object> loc = new ArrayList<>(List.of(LOC_BODY));
            for (JacksonException.Reference reference : mappingError.getPath()) {
                loc.add(reference.getPropertyName() != null ? reference.getPropertyName() : reference.getIndex());
            }
            return new Item(TYPE_TYPE_ERROR, loc, MSG_INVALID_VALUE, null);
        }
        // 본문이 아예 없을 때. Jackson까지 가지 않으므로 cause가 없다.
        return new Item(TYPE_MISSING, List.of(LOC_BODY), MSG_FIELD_REQUIRED, null);
    }

    private static List<Object> splitFieldPath(String field) {
        List<Object> parts = new ArrayList<>();
        Matcher matcher = FIELD_PATH.matcher(field);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                parts.add(Integer.valueOf(matcher.group(1)));
            } else {
                parts.add(matcher.group(2) != null ? matcher.group(2) : matcher.group(3));
            }
        }
        return parts;
    }

    private static String sourceOf(MethodParameter parameter) {
        if (parameter.hasParameterAnnotation(PathVariable.class)) {
            return LOC_PATH;
        }
        if (parameter.hasParameterAnnotation(RequestHeader.class)) {
            return LOC_HEADER;
        }
        return parameter.hasParameterAnnotation(RequestBody.class) ? LOC_BODY : LOC_QUERY;
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        return name != null ? name : "arg" + parameter.getParameterIndex();
    }

    private static String messageOf(ObjectError error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : MSG_INVALID_VALUE;
    }

    private static ResponseEntity<Object> respondValidation(List<Item> items, HttpHeaders headers) {
        return respond(new ApiValidationError(items), headers, VALIDATION_STATUS);
    }

    private static ResponseEntity<Object> respond(Object body, HttpHeaders headers, HttpStatusCode status) {
        return ResponseEntity.status(status)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private static String reasonOf(HttpStatusCode status) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}
