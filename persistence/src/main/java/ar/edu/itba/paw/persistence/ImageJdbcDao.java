// Implementa el contrato ImageDao (modulo persistence-contracts).
package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Image;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class ImageJdbcDao implements ImageDao {

    private static final RowMapper<Image> ROW_MAPPER = (rs, rowNum) -> {
        final Image image = new Image();
        image.setId(rs.getLong("id"));
        image.setData(rs.getBytes("data"));
        image.setContentType(rs.getString("content_type"));
        image.setFileName(rs.getString("file_name"));
        image.setFileSize(rs.getObject("file_size", Integer.class));
        image.setChecksum(rs.getString("checksum"));
        image.setUploadedAt(rs.getTimestamp("uploaded_at"));
        return image;
    };

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert jdbcInsert;

    @Autowired
    public ImageJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.jdbcInsert = new SimpleJdbcInsert(ds)
                .withTableName("image")
                .usingGeneratedKeyColumns("id")
                .usingColumns("data", "content_type", "file_name", "file_size", "checksum");
    }

    @Override
    public Image create(final Image image) {
        final Map<String, Object> args = new HashMap<>();
        args.put("data", image.getData());
        args.put("content_type", image.getContentType());
        args.put("file_name", image.getFileName());
        args.put("file_size", image.getFileSize());
        args.put("checksum", image.getChecksum());

        final Number id = jdbcInsert.executeAndReturnKey(args);
        image.setId(id.longValue());
        return image;
    }

    @Override
    public Optional<Image> findById(final long id) {
        return jdbcTemplate.query("SELECT * FROM image WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }
}
