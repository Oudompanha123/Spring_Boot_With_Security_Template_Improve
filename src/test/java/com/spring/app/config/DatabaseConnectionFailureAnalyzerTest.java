package com.spring.app.config;

import org.hibernate.HibernateException;
import org.hibernate.service.spi.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseConnectionFailureAnalyzerTest {

    private final DatabaseConnectionFailureAnalyzer analyzer = new DatabaseConnectionFailureAnalyzer();

    /** The chain Spring actually propagates: no SQLException anywhere in it. */
    private Throwable realWorldChain() {
        HibernateException cause = new HibernateException(
                "Unable to determine Dialect without JDBC metadata (please set "
                        + "'jakarta.persistence.jdbc.url' for common cases or 'hibernate.dialect' when a "
                        + "custom Dialect implementation must be provided)");
        ServiceException service = new ServiceException("Unable to create requested service", cause);
        return new BeanCreationException("Error creating bean with name 'entityManagerFactory'", service);
    }

    @Test
    @DisplayName("the dialect failure is explained")
    void explainsTheDialectFailure() {
        Throwable failure = realWorldChain();

        FailureAnalysis analysis = analyzer.analyze(failure);

        assertThat(analysis).isNotNull();
        assertThat(analysis.getDescription()).contains("could not obtain a single database connection");
        // The point of the action text: say where the real reason is, since Hibernate logs it
        // separately instead of attaching it.
        assertThat(analysis.getAction()).contains("SqlExceptionHelper");
        assertThat(analysis.getAction()).contains("Database may be already in use");
        assertThat(analysis.getAction()).contains("appdb.lock.db");
    }

    @Test
    @DisplayName("no SQLException is required, because the real chain has none")
    void doesNotDependOnASqlException() {
        Throwable failure = realWorldChain();

        // Documents why this analyzer is keyed on HibernateException: an analyzer keyed on
        // SQLException looks correct and never fires, because nothing in this chain is one.
        for (Throwable t = failure; t != null; t = t.getCause()) {
            assertThat(t).isNotInstanceOf(java.sql.SQLException.class);
        }
        assertThat(analyzer.analyze(failure)).isNotNull();
    }

    @Test
    @DisplayName("unrelated Hibernate failures are left to normal reporting")
    void ignoresUnrelatedHibernateFailures() {
        assertThat(analyzer.analyze(new HibernateException("could not extract ResultSet"))).isNull();
        assertThat(analyzer.analyze(new HibernateException((String) null))).isNull();
        assertThat(analyzer.analyze(new IllegalStateException("not hibernate at all"))).isNull();
    }
}
