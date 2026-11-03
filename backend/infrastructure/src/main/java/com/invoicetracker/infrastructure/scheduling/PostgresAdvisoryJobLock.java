package com.invoicetracker.infrastructure.scheduling;

import com.invoicetracker.application.port.JobLock;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Uses a PostgreSQL session-level advisory lock held on a dedicated connection for the duration of
 * the job. If the instance dies, the connection closes and Postgres releases the lock
 * automatically, so a crashed node can never block the job forever.
 */
@Component
class PostgresAdvisoryJobLock implements JobLock {

    private static final Logger log = LoggerFactory.getLogger(PostgresAdvisoryJobLock.class);

    private final DataSource dataSource;

    PostgresAdvisoryJobLock(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> Optional<T> tryRun(String jobName, Supplier<T> work) {
        long key = lockKey(jobName);
        try (Connection connection = dataSource.getConnection()) {
            if (!execute(connection, "SELECT pg_try_advisory_lock(?)", key)) {
                return Optional.empty();
            }
            try {
                return Optional.ofNullable(work.get());
            } finally {
                if (!execute(connection, "SELECT pg_advisory_unlock(?)", key)) {
                    log.warn("Advisory lock for job {} was not held at unlock time", jobName);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not acquire job lock for " + jobName, e);
        }
    }

    static long lockKey(String jobName) {
        var crc = new CRC32();
        crc.update(jobName.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }

    private static boolean execute(Connection connection, String sql, long key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }
}
