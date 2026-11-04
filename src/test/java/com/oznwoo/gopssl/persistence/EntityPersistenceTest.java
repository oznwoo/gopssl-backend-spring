package com.oznwoo.gopssl.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.oznwoo.gopssl.customer.entity.Customer;
import com.oznwoo.gopssl.customer.repository.CustomerRepository;
import com.oznwoo.gopssl.sale.entity.Sale;
import com.oznwoo.gopssl.sale.entity.SaleTreatment;
import com.oznwoo.gopssl.sale.repository.SaleRepository;
import com.oznwoo.gopssl.sale.repository.SaleTreatmentRepository;
import com.oznwoo.gopssl.treatment.entity.Treatment;
import com.oznwoo.gopssl.treatment.entity.TreatmentCategory;
import com.oznwoo.gopssl.treatment.repository.TreatmentCategoryRepository;
import com.oznwoo.gopssl.treatment.repository.TreatmentRepository;
import com.oznwoo.gopssl.user.entity.User;
import com.oznwoo.gopssl.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 엔티티별 저장·조회와 V1의 FK 동작을 실제 PostgreSQL에서 확인한다. 각 테스트는 끝나면 롤백된다. */
@SpringBootTest
@Testcontainers
@Transactional
class EntityPersistenceTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16");

    @DynamicPropertySource
    static void environment(DynamicPropertyRegistry registry) {
        registry.add("DB_URL", POSTGRES::getJdbcUrl);
        registry.add("DB_USERNAME", POSTGRES::getUsername);
        registry.add("DB_PASSWORD", POSTGRES::getPassword);
    }

    @PersistenceContext
    EntityManager em;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    UserRepository userRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    TreatmentCategoryRepository treatmentCategoryRepository;

    @Autowired
    TreatmentRepository treatmentRepository;

    @Autowired
    SaleRepository saleRepository;

    @Autowired
    SaleTreatmentRepository saleTreatmentRepository;

    @Test
    void userRoundTripsAndGetsCreatedAtFromDatabase() {
        User user = newUser("user@example.com");
        user.setName("이름");
        user.setStoreName("가게");
        user.setAddress("주소");
        user.setAddressDetail("상세");
        user.setZipCode("04763");
        userRepository.saveAndFlush(user);

        // 다시 읽기 전에 이미 채워져 있어야 한다 (@Generated).
        assertThat(user.getCreatedAt()).isNotNull();
        em.clear();

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getEmail()).isEqualTo("user@example.com");
        assertThat(found.getName()).isEqualTo("이름");
        assertThat(found.getStoreName()).isEqualTo("가게");
        assertThat(found.getAddress()).isEqualTo("주소");
        assertThat(found.getAddressDetail()).isEqualTo("상세");
        assertThat(found.getZipCode()).isEqualTo("04763");
        assertThat(found.getCreatedAt()).isEqualTo(user.getCreatedAt());
    }

    @Test
    void customerRoundTripsWithV1Defaults() {
        User user = userRepository.save(newUser("customer@example.com"));
        Customer customer = new Customer();
        customer.setUser(user);
        customer.setPhoneNumber("010-0000-0000");
        customerRepository.saveAndFlush(customer);
        em.clear();

        Customer found = customerRepository.findById(customer.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("신규고객님");
        assertThat(found.getPhoneNumber()).isEqualTo("010-0000-0000");
        assertThat(found.isDeleted()).isFalse();
        assertThat(found.getDeletedAt()).isNull();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(Hibernate.isInitialized(found.getUser())).as("user는 지연 로딩").isFalse();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void instantIsStoredAsTheSamePointInTime() {
        User user = userRepository.save(newUser("time@example.com"));
        Customer customer = new Customer();
        customer.setUser(user);
        Instant deletedAt = Instant.parse("2026-01-01T00:00:00Z");
        customer.setDeleted(true);
        customer.setDeletedAt(deletedAt);
        customerRepository.saveAndFlush(customer);
        em.clear();

        assertThat(customerRepository.findById(customer.getId()).orElseThrow().getDeletedAt()).isEqualTo(deletedAt);
        Boolean sameInstant = jdbcTemplate.queryForObject(
                "SELECT deleted_at = TIMESTAMP WITH TIME ZONE '2026-01-01 09:00:00+09' FROM customers WHERE id = ?",
                Boolean.class, customer.getId());
        assertThat(sameInstant).isTrue();
    }

    @Test
    void treatmentCategoryRoundTrips() {
        User user = userRepository.save(newUser("category@example.com"));
        TreatmentCategory category = newCategory(user);
        treatmentCategoryRepository.saveAndFlush(category);
        em.clear();

        TreatmentCategory found = treatmentCategoryRepository.findById(category.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("카테고리");
        assertThat(found.getDisplayOrder()).isZero();
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void treatmentRoundTrips() {
        User user = userRepository.save(newUser("treatment@example.com"));
        TreatmentCategory category = treatmentCategoryRepository.save(newCategory(user));
        Treatment treatment = newTreatment(user);
        treatment.setCategory(category);
        treatmentRepository.saveAndFlush(treatment);
        em.clear();

        Treatment found = treatmentRepository.findById(treatment.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("시술");
        assertThat(found.getDefaultPrice()).isEqualTo(30000);
        assertThat(found.getDisplayOrder()).isZero();
        assertThat(Hibernate.isInitialized(found.getCategory())).as("category는 지연 로딩").isFalse();
        assertThat(found.getCategory().getId()).isEqualTo(category.getId());
    }

    @Test
    void saleRoundTripsIncludingExtraPricesJson() {
        User user = userRepository.save(newUser("sale@example.com"));
        Sale sale = newSale(user, null);
        sale.setDetail("메모");
        sale.setPaymentType("card");
        sale.setTotalAmount(35000);
        sale.setNumOfVisits(2);
        sale.setExtraPrices(Map.of("지정비", 5000));
        saleRepository.saveAndFlush(sale);

        assertThat(sale.getCreatedAt()).isNotNull();
        em.clear();

        Sale found = saleRepository.findById(sale.getId()).orElseThrow();
        assertThat(found.getSaleDate()).isEqualTo(LocalDate.of(2026, 1, 2));
        assertThat(found.getDetail()).isEqualTo("메모");
        assertThat(found.getPaymentType()).isEqualTo("card");
        assertThat(found.getTotalAmount()).isEqualTo(35000);
        assertThat(found.getNumOfVisits()).isEqualTo(2);
        assertThat(found.getExtraPrices()).containsExactly(Map.entry("지정비", 5000));
        assertThat(found.getCustomer()).isNull();

        String stored = jdbcTemplate.queryForObject(
                "SELECT extra_prices ->> '지정비' FROM sales WHERE id = ?", String.class, sale.getId());
        assertThat(stored).isEqualTo("5000");
    }

    @Test
    void extraPricesWrittenByV1AsJsonNullReadsAsNull() {
        // v1(SQLAlchemy JSON)은 None을 SQL NULL이 아니라 JSON null로 저장한다.
        User user = userRepository.save(newUser("jsonnull@example.com"));
        Sale sale = saleRepository.saveAndFlush(newSale(user, null));
        jdbcTemplate.update("UPDATE sales SET extra_prices = 'null'::json WHERE id = ?", sale.getId());
        em.clear();

        assertThat(saleRepository.findById(sale.getId()).orElseThrow().getExtraPrices()).isNull();
    }

    @Test
    void saleTreatmentRoundTripsThroughSaleCascade() {
        User user = userRepository.save(newUser("line@example.com"));
        Treatment treatment = treatmentRepository.save(newTreatment(user));
        Sale sale = newSale(user, null);
        SaleTreatment line = newLine(treatment);
        sale.addTreatment(line);
        saleRepository.saveAndFlush(sale);
        em.clear();

        SaleTreatment found = saleTreatmentRepository.findById(line.getId()).orElseThrow();
        assertThat(found.getPrice()).isEqualTo(30000);
        assertThat(found.getSale().getId()).isEqualTo(sale.getId());
        assertThat(found.getTreatment().getId()).isEqualTo(treatment.getId());
        em.clear();

        Sale foundSale = saleRepository.findById(sale.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(foundSale.getTreatments())).as("treatments는 지연 로딩").isFalse();
        assertThat(foundSale.getTreatments()).hasSize(1);
    }

    @Test
    void removingLineFromSaleDeletesIt() {
        User user = userRepository.save(newUser("orphan@example.com"));
        Sale sale = newSale(user, null);
        SaleTreatment line = newLine(treatmentRepository.save(newTreatment(user)));
        sale.addTreatment(line);
        saleRepository.saveAndFlush(sale);
        em.clear();

        saleRepository.findById(sale.getId()).orElseThrow().getTreatments().clear();
        em.flush();
        em.clear();

        assertThat(saleTreatmentRepository.findById(line.getId())).isEmpty();
    }

    @Test
    void deletingCustomerSetsSaleCustomerIdToNull() {
        User user = userRepository.save(newUser("fk1@example.com"));
        Customer customer = new Customer();
        customer.setUser(user);
        customerRepository.save(customer);
        Sale sale = saleRepository.saveAndFlush(newSale(user, customer));
        em.clear();

        customerRepository.deleteById(customer.getId());
        em.flush();
        em.clear();

        assertThat(customerRepository.findById(customer.getId())).isEmpty();
        assertThat(saleRepository.findById(sale.getId()).orElseThrow().getCustomer()).isNull();
    }

    @Test
    void deletingSaleInDatabaseDeletesItsSaleTreatments() {
        User user = userRepository.save(newUser("fk2@example.com"));
        Sale sale = newSale(user, null);
        SaleTreatment line = newLine(treatmentRepository.save(newTreatment(user)));
        sale.addTreatment(line);
        saleRepository.saveAndFlush(sale);
        em.clear();

        // JPA cascade를 거치지 않고 DB의 ON DELETE CASCADE만으로 지워지는지 본다.
        jdbcTemplate.update("DELETE FROM sales WHERE id = ?", sale.getId());

        assertThat(saleRepository.findById(sale.getId())).isEmpty();
        assertThat(saleTreatmentRepository.findById(line.getId())).isEmpty();
    }

    @Test
    void deletingSaleThroughRepositoryAlsoDeletesItsSaleTreatments() {
        User user = userRepository.save(newUser("fk3@example.com"));
        Sale sale = newSale(user, null);
        SaleTreatment line = newLine(treatmentRepository.save(newTreatment(user)));
        sale.addTreatment(line);
        saleRepository.saveAndFlush(sale);
        em.clear();

        saleRepository.deleteById(sale.getId());
        em.flush();
        em.clear();

        assertThat(saleTreatmentRepository.findById(line.getId())).isEmpty();
    }

    private static User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("not-a-real-hash");
        return user;
    }

    private static TreatmentCategory newCategory(User user) {
        TreatmentCategory category = new TreatmentCategory();
        category.setUser(user);
        category.setName("카테고리");
        return category;
    }

    private static Treatment newTreatment(User user) {
        Treatment treatment = new Treatment();
        treatment.setUser(user);
        treatment.setName("시술");
        treatment.setDefaultPrice(30000);
        return treatment;
    }

    private static Sale newSale(User user, Customer customer) {
        Sale sale = new Sale();
        sale.setUser(user);
        sale.setCustomer(customer);
        sale.setSaleDate(LocalDate.of(2026, 1, 2));
        return sale;
    }

    private static SaleTreatment newLine(Treatment treatment) {
        SaleTreatment line = new SaleTreatment();
        line.setTreatment(treatment);
        line.setPrice(30000);
        return line;
    }
}
