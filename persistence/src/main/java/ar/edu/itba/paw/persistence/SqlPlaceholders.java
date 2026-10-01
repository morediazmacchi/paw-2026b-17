package ar.edu.itba.paw.persistence;

// "?, ?, ?" para un IN (...) de n elementos. Lo usan EventJdbcDao y
// UserJdbcDao para los findByIds() en lote (WHERE id IN (...)).
final class SqlPlaceholders {

    private SqlPlaceholders() {
    }

    static String of(final int count) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?");
        }
        return sb.toString();
    }
}
