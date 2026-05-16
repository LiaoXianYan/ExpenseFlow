package com.expenseflow.system;

import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import com.expenseflow.common.test.TestJwtHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestConfig.class)
public abstract class BaseControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected RedisTemplate<String, String> redisTemplate;

    @MockBean
    protected RedisConnectionFactory redisConnectionFactory;

    @BeforeEach
    void setUpBase() {
        ExpenseFlowTenantLineHandler.setTenant(0L);
    }

    @AfterEach
    void tearDownBase() {
        ExpenseFlowTenantLineHandler.clear();
    }

    protected ResultActions getWithJwt(String url, Long userId, String... roles) throws Exception {
        return mockMvc.perform(get(url)
            .header("Authorization", TestJwtHelper.authHeader(userId, roles))
            .header("X-Tenant-Id", "0")
            .contentType(MediaType.APPLICATION_JSON));
    }

    protected ResultActions postWithJwt(String url, Object body, Long userId, String... roles) throws Exception {
        return mockMvc.perform(post(url)
            .header("Authorization", TestJwtHelper.authHeader(userId, roles))
            .header("X-Tenant-Id", "0")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)));
    }
}
