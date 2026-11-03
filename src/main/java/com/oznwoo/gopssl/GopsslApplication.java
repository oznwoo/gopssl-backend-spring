package com.oznwoo.gopssl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// 기본 인메모리 사용자(로그에 임시 비밀번호 출력)를 만들지 않는다. 인증은 이후 JWT 티켓에서 구현한다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class GopsslApplication {

    public static void main(String[] args) {
        SpringApplication.run(GopsslApplication.class, args);
    }
}
