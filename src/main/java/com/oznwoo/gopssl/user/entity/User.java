package com.oznwoo.gopssl.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    @Column(length = 50)
    private String name;

    @Column(length = 100, nullable = false, unique = true)
    private String email;

    @Column(columnDefinition = "text", nullable = false)
    private String password;

    @Column(name = "store_name", length = 100)
    private String storeName;

    @Column(columnDefinition = "text")
    private String address;

    @Column(name = "address_detail", columnDefinition = "text")
    private String addressDetail;

    @Column(name = "zip_code", columnDefinition = "text")
    private String zipCode;

    /** DB 기본값 now()를 쓴다. INSERT에서 빼고, 저장 직후 DB가 만든 값을 다시 읽어온다. */
    @Generated
    @Column(name = "created_at", insertable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private Instant createdAt;
}
