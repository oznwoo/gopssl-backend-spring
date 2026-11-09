package com.oznwoo.gopssl.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 컨텍스트가 뜨면 {@code ddl-auto=validate}를 통과한 것이다. 다만 validate는 매핑된 컬럼의 존재와 타입만 본다.
 * 빠뜨린 컬럼, nullable, 길이는 보지 않으므로 그 부분을 {@code information_schema}와 직접 비교한다.
 */
@SpringBootTest
@Testcontainers
class EntitySchemaConsistencyTest {

    private static final int JPA_DEFAULT_LENGTH = 255;

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private record ColumnSpec(boolean nullable, Integer length) {
    }

    @Test
    void contextStartsWithValidate() {
        assertThat(entityManagerFactory.getProperties()).containsEntry("hibernate.hbm2ddl.auto", "validate");
    }

    @Test
    void everyBusinessTableHasAnEntity() {
        List<String> tables = jdbcTemplate.queryForList(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'
                """,
                String.class);

        Set<String> mapped = entityClasses().stream()
                .map(type -> type.getAnnotation(Table.class).name())
                .collect(Collectors.toSet());
        assertThat(mapped).containsExactlyInAnyOrderElementsOf(tables);
    }

    @Test
    void mappedColumnsMatchSchemaIncludingNullableAndLength() {
        for (Class<?> type : entityClasses()) {
            String table = type.getAnnotation(Table.class).name();
            assertThat(mappedColumns(type)).as(table).isEqualTo(databaseColumns(table));
        }
    }

    private List<Class<?>> entityClasses() {
        return entityManagerFactory.getMetamodel().getEntities().stream()
                .<Class<?>>map(EntityType::getJavaType)
                .toList();
    }

    private Map<String, ColumnSpec> databaseColumns(String table) {
        Map<String, ColumnSpec> columns = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT column_name, is_nullable, character_maximum_length
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ?
                """,
                rs -> {
                    columns.put(
                            rs.getString("column_name"),
                            new ColumnSpec(
                                    "YES".equals(rs.getString("is_nullable")),
                                    rs.getObject("character_maximum_length", Integer.class)));
                },
                table);
        return columns;
    }

    private static Map<String, ColumnSpec> mappedColumns(Class<?> type) {
        Map<String, ColumnSpec> columns = new HashMap<>();
        for (Field field : type.getDeclaredFields()) {
            Column column = field.getAnnotation(Column.class);
            JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
            if (column != null) {
                columns.put(
                        column.name().isEmpty() ? field.getName() : column.name(),
                        new ColumnSpec(column.nullable(), lengthOf(field, column)));
            } else if (joinColumn != null) {
                columns.put(joinColumn.name(), new ColumnSpec(joinColumn.nullable(), null));
            } else if ("id".equals(field.getName())) {
                columns.put("id", new ColumnSpec(false, null));
            }
        }
        return columns;
    }

    /** 길이는 {@code length}를 직접 적은 VARCHAR(n)에만 있다. text, varchar(무제한)는 columnDefinition으로 적는다. */
    private static Integer lengthOf(Field field, Column column) {
        boolean sizedString = field.getType() == String.class && column.columnDefinition().isEmpty();
        return sizedString && column.length() != JPA_DEFAULT_LENGTH ? Integer.valueOf(column.length()) : null;
    }
}
