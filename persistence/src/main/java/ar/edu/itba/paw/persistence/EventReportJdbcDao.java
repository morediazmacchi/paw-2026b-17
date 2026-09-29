// Implementa el contrato EventReportDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.EventReport;
import ar.edu.itba.paw.model.ReportStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class EventReportJdbcDao implements EventReportDao {

    private static final RowMapper<EventReport> ROW_MAPPER = (rs, rowNum) -> {
        final EventReport report = new EventReport();
        report.setId(rs.getLong("id"));
        report.setEventId(rs.getLong("event_id"));
        report.setReporterUserId(rs.getLong("reporter_user_id"));
        report.setReason(rs.getString("reason"));
        report.setCreatedAt(rs.getTimestamp("created_at"));
        report.setStatus(ReportStatus.valueOf(rs.getString("status")));
        final Integer resolvedBy = rs.getObject("resolved_by", Integer.class);
        report.setResolvedByUserId(resolvedBy == null ? null : resolvedBy.longValue());
        report.setResolvedAt(rs.getTimestamp("resolved_at"));
        return report;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public EventReportJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("event_report")
                .usingGeneratedKeyColumns("id")
                .usingColumns("event_id", "reporter_user_id", "reason");
    }

    @Override
    public EventReport create(final EventReport report) {
        final Map<String, Object> args = new HashMap<>();
        args.put("event_id", report.getEventId());
        args.put("reporter_user_id", report.getReporterUserId());
        args.put("reason", report.getReason());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        report.setId(id.longValue());
        return report;
    }

    @Override
    public Optional<EventReport> findById(final long id) {
        return jdbcTemplate.query("SELECT * FROM event_report WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public List<EventReport> findPending() {
        return jdbcTemplate.query(
                "SELECT * FROM event_report WHERE status = ? ORDER BY created_at ASC",
                ROW_MAPPER, ReportStatus.PENDING.name());
    }

    @Override
    public boolean existsPendingByEventAndReporter(final long eventId, final long reporterUserId) {
        final Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM event_report WHERE event_id = ? AND reporter_user_id = ? AND status = ?",
                Long.class, eventId, reporterUserId, ReportStatus.PENDING.name());
        return count != null && count > 0;
    }

    @Override
    public void resolve(final long reportId, final ReportStatus status, final long resolvedByUserId) {
        jdbcTemplate.update(
                "UPDATE event_report SET status = ?, resolved_by = ?, resolved_at = ? "
                        + "WHERE id = ? AND status = ?",
                status.name(), resolvedByUserId, new Timestamp(System.currentTimeMillis()),
                reportId, ReportStatus.PENDING.name());
    }

    @Override
    public void resolveAllPendingForEvent(final long eventId, final ReportStatus status, final long resolvedByUserId) {
        jdbcTemplate.update(
                "UPDATE event_report SET status = ?, resolved_by = ?, resolved_at = ? "
                        + "WHERE event_id = ? AND status = ?",
                status.name(), resolvedByUserId, new Timestamp(System.currentTimeMillis()),
                eventId, ReportStatus.PENDING.name());
    }
}
