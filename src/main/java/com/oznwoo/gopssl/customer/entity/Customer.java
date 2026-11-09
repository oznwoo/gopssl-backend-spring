package com.oznwoo.gopssl.customer.entity;

import com.oznwoo.gopssl.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** DB에 DEFAULT가 없어서 v1 모델의 기본값을 여기서 준다. */
    @Column(length = 50)
    private String name = "신규고객님";

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(columnDefinition = "text")
    private String detail;

    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;

    /** DB에 DEFAULT가 없다. 기본값 false는 v1 모델과 같다. */
    @Column(name = "is_deleted", nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
