package com.oznwoo.gopssl.treatment.repository;

import com.oznwoo.gopssl.treatment.entity.Treatment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreatmentRepository extends JpaRepository<Treatment, Integer> {
}
