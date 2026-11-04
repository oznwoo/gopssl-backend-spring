package com.oznwoo.gopssl.treatment.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "treatment_categories")
@Getter
@Setter
@NoArgsConstructor
public class TreatmentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Integer id;

    /** V1에서 NULL을 허용한다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(columnDefinition = "varchar", nullable = false)
    private String name;

    /** DB에 DEFAULT가 없다. 기본값 0은 v1 모델과 같다. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
}
