package com.vernflow.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class RagStorageConfiguration {

    @Bean("ragDataSource")
    public DataSource ragDataSource(RagStoreDataSourceProperties properties) {
        return DataSourceBuilder.create()
                .driverClassName(properties.driverClassName())
                .url(properties.url())
                .username(properties.username())
                .password(properties.password())
                .build();
    }

    @Bean("ragJdbcTemplate")
    public JdbcTemplate ragJdbcTemplate(@Qualifier("ragDataSource") DataSource ragDataSource) {
        return new JdbcTemplate(ragDataSource);
    }

    @Bean("ragTransactionManager")
    public PlatformTransactionManager ragTransactionManager(@Qualifier("ragDataSource") DataSource ragDataSource) {
        return new DataSourceTransactionManager(ragDataSource);
    }

    @Bean(initMethod = "migrate")
    @ConditionalOnProperty(prefix = "app.rag-store.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    public Flyway ragFlyway(
            @Qualifier("ragDataSource") DataSource ragDataSource,
            RagStoreFlywayProperties properties
    ) {
        return Flyway.configure()
                .dataSource(ragDataSource)
                .locations(properties.locations().toArray(String[]::new))
                .table(properties.table())
                .baselineOnMigrate(true)
                .load();
    }
}
