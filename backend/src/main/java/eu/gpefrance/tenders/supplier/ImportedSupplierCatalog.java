package eu.gpefrance.tenders.supplier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The prices imported from the files of the suppliers, read in the database.
 */
@Component
public class ImportedSupplierCatalog implements SupplierCatalog {

	private static final int MAX_WORDS = 6;

	private static final String SELECT = """
			SELECT o.*, s.name AS supplier_name FROM supplier_offer o JOIN supplier s ON s.id = o.supplier_id
			""";

	private final JdbcTemplate jdbc;

	public ImportedSupplierCatalog(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * Offers whose reference starts with the query, or whose designation holds
	 * every word of it; exact references first.
	 */
	@Override
	public List<Offer> search(String query, Long supplierId, int limit) {
		String text = query == null ? "" : query.strip();
		if (text.isEmpty()) {
			return List.of();
		}
		List<Object> args = new ArrayList<>();
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE ");
		if (supplierId != null) {
			sql.append("o.supplier_id = ? AND ");
			args.add(supplierId);
		}
		String prefix = like(text) + "%";
		sql.append("(o.supplier_ref ILIKE ? OR o.manufacturer_ref ILIKE ? OR o.ean = ?");
		args.addAll(List.of(prefix, prefix, text));
		List<String> words = Arrays.stream(text.split("\\s+")).filter(word -> word.length() > 1).limit(MAX_WORDS).toList();
		if (!words.isEmpty()) {
			sql.append(" OR (");
			for (int i = 0; i < words.size(); i++) {
				sql.append(i == 0 ? "" : " AND ").append("o.designation ILIKE ?");
				args.add("%" + like(words.get(i)) + "%");
			}
			sql.append(')');
		}
		sql.append(") ORDER BY (upper(o.supplier_ref) = upper(?) OR upper(o.manufacturer_ref) = upper(?)) DESC, ")
			.append("o.designation, o.id LIMIT ?");
		args.addAll(List.of(text, text, Math.max(1, Math.min(limit, 100))));
		return jdbc.query(sql.toString(), ImportedSupplierCatalog::offer, args.toArray());
	}

	@Override
	public Optional<Offer> find(Long offerId) {
		return jdbc.query(SELECT + " WHERE o.id = ?", ImportedSupplierCatalog::offer, offerId).stream().findFirst();
	}

	@Override
	public List<Offer> list(Long supplierId, int limit) {
		return jdbc.query(SELECT + " WHERE o.supplier_id = ? ORDER BY o.designation, o.id LIMIT ?",
				ImportedSupplierCatalog::offer, supplierId, limit);
	}

	/** The text searched as written: % and _ are no wildcards. */
	private static String like(String text) {
		return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private static Offer offer(ResultSet row, int number) throws SQLException {
		Timestamp imported = row.getTimestamp("imported_at");
		return new Offer(row.getLong("id"), row.getLong("supplier_id"), row.getString("supplier_name"),
				row.getString("supplier_ref"), row.getString("manufacturer_ref"), row.getString("ean"),
				row.getString("designation"), row.getString("purchase_unit"), row.getBigDecimal("pack_quantity"),
				row.getBigDecimal("list_price"), row.getBigDecimal("discount_rate"), row.getBigDecimal("net_price"),
				row.getObject("valid_from", java.time.LocalDate.class),
				row.getObject("valid_until", java.time.LocalDate.class), imported.toInstant(),
				row.getString("manufacturer_code"), row.getBigDecimal("eco_tax"), row.getString("price_source"));
	}

}
