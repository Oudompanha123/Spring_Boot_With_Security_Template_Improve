package com.spring.app.config;

import org.hibernate.HibernateException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns Hibernate's "Unable to determine Dialect without JDBC metadata" into a checklist.
 *
 * <h2>Why this message needs help</h2>
 *
 * It is what every "could not connect to the database" failure looks like from the outside, whatever
 * the actual cause: the file was locked, the directory was not writable, the server was unreachable,
 * the URL was missing. Hibernate needs one connection to read the dialect from, does not get it, and
 * reports the consequence rather than the reason.
 *
 * <p>The reason <em>is</em> logged — by {@code SqlExceptionHelper}, a few lines above, as a separate
 * ERROR. It is not attached to this exception: the chain that propagates is
 * {@code BeanCreationException -> ServiceException -> HibernateException} and stops there, with no
 * {@code SQLException} anywhere in it. So the useful sentence and the fatal error are two unrelated
 * entries in the log, and under Gradle the whole thing arrives as
 * {@code finished with non-zero exit value 1}.
 *
 * <p>Keyed on {@link HibernateException} for exactly that reason — an analyzer keyed on
 * {@code SQLException} looks like the right idea and never matches. Matching on the message is not
 * elegant, but it is the only thing this exception carries; anything else returns {@code null} and
 * is left to normal reporting.
 */
public class DatabaseConnectionFailureAnalyzer extends AbstractFailureAnalyzer<HibernateException> {

    private static final String NO_METADATA = "Unable to determine Dialect without JDBC metadata";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, HibernateException cause) {
        // The whole chain, not cause.getMessage(). The first HibernateException found walking down
        // is usually a ServiceException - which extends HibernateException - carrying "Unable to
        // create requested service ... due to: <the real message>". Matching on that one works only
        // because it happens to embed the nested text; the exception that actually says it is
        // deeper. Scanning every message makes this independent of how Hibernate composes them.
        if (!chainMentions(rootFailure, NO_METADATA)) {
            return null;
        }

        String description = "Hibernate could not obtain a single database connection, so it could "
                + "not determine which SQL dialect to use. The database was unreachable, refused "
                + "the connection, or could not be opened.";

        String action = """
                The actual reason is in the log ABOVE this block, on the ERROR line from
                o.h.engine.jdbc.spi.SqlExceptionHelper. Hibernate does not attach it to this
                exception, so read that line first - it names which of these it is.

                  "Database may be already in use" (H2)
                    Another instance is running, or one died without releasing the lock:
                      netstat -ano | findstr :8088      then    taskkill /F /PID <pid>
                      rm -f data/h2/appdb.lock.db       # only after a kill or crash

                  "Permission denied" / cannot create the database file (H2)
                    The process cannot write the path in spring.datasource.url. In a container
                    running as a non-root user, point SPRING_DATASOURCE_URL at a writable
                    directory, or make the target directory owned by that user.

                  "Connection refused" / "could not connect" (PostgreSQL)
                    The server is not running, or the host, port, database or credentials are
                    wrong. Check DB_HOST, DB_PORT, DB_NAME, DB_USERNAME, DB_PASSWORD - or
                    DB_URL/SPRING_DATASOURCE_URL if you set the whole URL yourself.

                  No URL at all
                    spring.datasource.url resolved to nothing. Confirm the active profile is the
                    one you meant: a profile that configures no datasource looks exactly like
                    this.""";

        return new FailureAnalysis(description, action, cause);
    }

    /** True when any message in the cause chain contains {@code needle}. Cycle-safe. */
    private boolean chainMentions(Throwable failure, String needle) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 32; depth++) {
            String message = current.getMessage();
            if (message != null && message.contains(needle)) {
                return true;
            }
            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return false;
    }
}
