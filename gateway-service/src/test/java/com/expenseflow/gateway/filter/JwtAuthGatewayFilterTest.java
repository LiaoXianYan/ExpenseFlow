package com.expenseflow.gateway.filter;

import com.expenseflow.gateway.GatewayApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayApplication.class,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.sentinel.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
    })
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class JwtAuthGatewayFilterTest {

    @Autowired WebTestClient webTestClient;

    @Test
    @DisplayName("白名单路径无需认证 (登陆接口不返回 401)")
    void shouldAllowLoginPathWithoutAuth() {
        webTestClient.get().uri("/system/auth/login")
            .exchange()
            .expectStatus().value(s -> assertThat(s).isNotEqualTo(401));
    }

    @Test
    @DisplayName("白名单路径无需认证 (Swagger 不返回 401)")
    void shouldAllowSwaggerPathWithoutAuth() {
        webTestClient.get().uri("/doc.html")
            .exchange()
            .expectStatus().value(s -> assertThat(s).isNotEqualTo(401));
    }

    @Test
    @DisplayName("无 Token 返回 401")
    void shouldReturn401WithoutToken() {
        webTestClient.get().uri("/expense/travel/1")
            .exchange()
            .expectStatus().isEqualTo(401);
    }

    @Test
    @DisplayName("无效 Token 返回 401")
    void shouldReturn401ForInvalidToken() {
        webTestClient.get().uri("/expense/travel/1")
            .header("Authorization", "Bearer invalid-token")
            .exchange()
            .expectStatus().isEqualTo(401);
    }

    @Test
    @DisplayName("白名单路径中 Actuator 不返回 401")
    void shouldAllowActuatorPathWithoutAuth() {
        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().value(s -> assertThat(s).isNotEqualTo(401));
    }
}
