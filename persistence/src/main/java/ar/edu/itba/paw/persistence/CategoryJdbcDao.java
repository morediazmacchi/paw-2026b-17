package ar.edu.itba.paw.persistence;

import ar.edu.itba.paw.model.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

@Repository
public class CategoryJdbcDao implements CategoryDao {

    private static final RowMapper<Category> ROW_MAPPER = (rs, rowNum) -> {
        final Category category = new Category();
        category.setId(rs.getLong("id"));
        category.setNombre(rs.getString("nombre"));
        return category;
    };

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public CategoryJdbcDao(final DataSource ds) {
        this.jdbcTemplate = new JdbcTemplate(ds);
    }

    @Override
    public List<Category> findAll() {
        return jdbcTemplate.query("SELECT * FROM categoria ORDER BY nombre", ROW_MAPPER);
    }

    @Override
    public Optional<Category> findById(final long id) {
        return jdbcTemplate.query("SELECT * FROM categoria WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }
}
