package com.expenseflow.approval.config;

import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlowableConfig {

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> flowableConfigurer() {
        return config -> {
            config.setActivityFontName("SimSun");
            config.setLabelFontName("SimSun");
            config.setAnnotationFontName("SimSun");
        };
    }
}
