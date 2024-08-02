package org.techbd.udi;

import java.sql.Connection;
import java.util.List;

import javax.sql.DataSource;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DataSourceConnectionProvider;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.DefaultDSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.techbd.conf.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManagerFactory;

@org.springframework.context.annotation.Configuration
@EnableJpaRepositories(basePackages = "org.techbd.udi")
@EnableTransactionManagement
public class UdiPrimeJpaConfig {

    @Autowired
    private JpaProperties jpaProperties;

    @Autowired
    private Environment environment;

    @Bean
    @Primary
    @Lazy
    @ConditionalOnProperty(name = "org.techbd.udi.prime.jdbc.url")
    @ConfigurationProperties(prefix = "org.techbd.udi.prime.jdbc")
    public DataSource udiPrimaryDataSource() {
        return DataSourceBuilder.create().build();
    }

    public record DataSourceHealthCheckResult(DataSource dataSrc, Exception error, Environment environment,
            String... expected) {

        public boolean isAlive() {
            return error == null;
        }

        public List<String> expectedConf() {
            return Configuration.checkProperties(environment, expected);
        }
    }

    public DataSourceHealthCheckResult udiPrimaryDataSrcHealth() {
        final var ds = udiPrimaryDataSource();
        try (Connection connection = ds.getConnection()) {
            return new DataSourceHealthCheckResult(ds, null, environment,
                    "${${SPRING_PROFILES_ACTIVE}_TECHBD_UDI_DS_PRIME_JDBC_URL:}");
        } catch (Exception e) {
            return new DataSourceHealthCheckResult(null, e, environment,
                    "${${SPRING_PROFILES_ACTIVE}_TECHBD_UDI_DS_PRIME_JDBC_URL:}");
        }
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(EntityManagerFactoryBuilder builder,
            DataSource udiPrimaryDataSource) {
        return builder
                .dataSource(udiPrimaryDataSource)
                .properties(jpaProperties.getProperties())
                .packages("org.techbd.udi.entity")
                .persistenceUnit("default")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public DSLContext dsl() {
        return new DefaultDSLContext(configuration());
    }

    public org.jooq.Configuration configuration() {
        final var jooqConfiguration = new DefaultConfiguration();
        jooqConfiguration.set(connectionProvider());
        jooqConfiguration.setSQLDialect(SQLDialect.POSTGRES);
        // jooqConfiguration
        // .set(new DefaultExecuteListenerProvider(exceptionTransformer()));
        return jooqConfiguration;
    }

    @Bean
    public DataSourceConnectionProvider connectionProvider() {
        return new DataSourceConnectionProvider(new TransactionAwareDataSourceProxy(udiPrimaryDataSource()));
    }

    /**
     * TODO: Add comment as to why this method is needed.
     *
     * @return
     */
    @Bean
    public ObjectMapper objectMapper() {
        return Configuration.objectMapper;
    }
}
