package com.oznwoo.gopssl.sale.entity;

import com.oznwoo.gopssl.customer.entity.Customer;
import com.oznwoo.gopssl.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @Column(name = "sale_date", nullable = false)
    private LocalDate saleDate;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "payment_type", length = 20)
    private String paymentType;

    @Column(name = "total_amount")
    private Integer totalAmount;

    @Column(name = "num_of_visits")
    private Integer numOfVisits;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    /** v1 스키마의 {@code Dict[str, int]}와 같은 모양이다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_prices", columnDefinition = "json")
    private Map<String, Integer> extraPrices;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Setter(AccessLevel.NONE)
    private List<SaleTreatment> treatments = new ArrayList<>();

    /** 양쪽 참조를 함께 맞춘다. 저장되는 쪽은 {@link SaleTreatment#getSale()}이다. */
    public void addTreatment(SaleTreatment treatment) {
        treatments.add(treatment);
        treatment.setSale(this);
    }
}
