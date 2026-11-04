package com.oznwoo.gopssl.user.repository;

import com.oznwoo.gopssl.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}
