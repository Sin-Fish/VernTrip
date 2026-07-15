package com.vernflow;

import com.vernflow.config.ProjectEnvLoader;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * VernFlow 后端启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class VernFlowApplication {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(VernFlowApplication.class);
        application.setDefaultProperties(ProjectEnvLoader.loadDefaults());
        application.run(args);
    }
}
