package com.expenseflow.approval;

import com.expenseflow.approval.feign.ExpenseFeignClient;
import com.expenseflow.approval.feign.SystemFeignClient;
import com.expenseflow.common.handler.ExpenseFlowTenantLineHandler;
import com.expenseflow.common.test.TestJwtHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
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
    protected ExpenseFeignClient expenseFeignClient;

    @MockBean
    protected SystemFeignClient systemFeignClient;

    @MockBean
    protected RabbitTemplate rabbitTemplate;

    @MockBean
    protected TaskService flowableTaskService;

    @MockBean
    protected RuntimeService flowableRuntimeService;

    @BeforeEach
    void setUpBase() {
        ExpenseFlowTenantLineHandler.setTenant(0L);

        // Set up mock TaskQuery to return empty list, avoiding NPE in listTasks()
        TaskQuery mockQuery = mock(TaskQuery.class);
        when(mockQuery.taskCandidateGroup(anyString())).thenReturn(mockQuery);
        when(mockQuery.taskAssignee(anyString())).thenReturn(mockQuery);
        when(mockQuery.orderByTaskCreateTime()).thenReturn(mockQuery);
        when(mockQuery.desc()).thenReturn(mockQuery);
        when(mockQuery.list()).thenReturn(Collections.emptyList());
        when(flowableTaskService.createTaskQuery()).thenReturn(mockQuery);
        when(flowableTaskService.getVariables(anyString())).thenReturn(Collections.emptyMap());
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
