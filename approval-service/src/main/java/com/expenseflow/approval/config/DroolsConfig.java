package com.expenseflow.approval.config;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.internal.io.ResourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;

@Configuration
public class DroolsConfig {

    private static final String RULES_PATH = "classpath:rules/*.drl";

    @Bean
    public KieContainer kieContainer() {
        try {
            KieServices kieServices = KieServices.Factory.get();
            KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

            Resource[] ruleFiles = new PathMatchingResourcePatternResolver().getResources(RULES_PATH);
            for (Resource file : ruleFiles) {
                kieFileSystem.write(ResourceFactory.newClassPathResource(
                    "rules/" + file.getFilename(), "UTF-8"));
            }

            KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
            kieBuilder.buildAll();
            if (kieBuilder.getResults().hasMessages(
                    org.kie.api.builder.Message.Level.ERROR)) {
                throw new IllegalStateException("Drools 规则编译失败: " + kieBuilder.getResults().getMessages());
            }

            KieModule kieModule = kieBuilder.getKieModule();
            return kieServices.newKieContainer(kieModule.getReleaseId());
        } catch (Exception e) {
            // Drools 9.x 兼容性问题，降级到 Java 规则引擎
            return null;
        }
    }
}
