package eu.gpefrance.tenders.supplier;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.ErrorResponseException;

import eu.gpefrance.tenders.supplier.SupplierCatalog.Offer;
import eu.gpefrance.tenders.supplier.SupplierConnector.Availability;
import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierConnector.Price;
import eu.gpefrance.tenders.supplier.SupplierConnector.Product;

/**
 * The prices and the stocks read from the services of the suppliers. A price
 * read replaces the one of the offer and is added to its history: the estimates
 * keep copying the price of an offer and never know where it comes from. The
 * calls are made out of any transaction, and never while an estimate is locked.
 */
@Service
public class SupplierPricingService {

	private static final Logger log = LoggerFactory.getLogger(SupplierPricingService.class);

	private static final int MAX_CACHED_AVAILABILITIES = 5000;

	private static final int FILLING_CHUNK = 100;

	private final Map<String, SupplierConnector> connectors;

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final SupplierCatalog catalog;

	private final SupplierPricingProperties properties;

	private final Clock clock;

	private final Map<String, CachedAvailability> availabilities = new ConcurrentHashMap<>();

	private final Map<String, CatalogueFilling> fillings = new ConcurrentHashMap<>();

	public SupplierPricingService(List<SupplierConnector> connectors, JdbcTemplate jdbc,
			TransactionTemplate transaction, SupplierCatalog catalog, SupplierPricingProperties properties,
			Clock clock) {
		this.connectors = connectors.stream()
			.collect(Collectors.toMap(SupplierConnector::supplierCode, Function.identity()));
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.catalog = catalog;
		this.properties = properties;
		this.clock = clock;
	}

	public enum RefreshStatus {

		/** The price was read from the supplier. */
		UPDATED,
		/** The price was read recently: the supplier was not asked again. */
		FRESH,
		/** The supplier gave no usable price: the offer keeps the one it had. */
		NOT_PRICED,
		/** No service for this supplier, or no manufacturer code on the offer. */
		NOT_CONNECTED

	}

	public record ConnectorResponse(String supplierCode, String supplierName, boolean available, String environment) {
	}

	/** @param priceLabel what the price stands for, as said by the supplier: "Prix unitaire € HT" */
	public record RefreshedOffer(Long offerId, RefreshStatus status, Offer offer, String priceLabel) {
	}

	/** @param availability null when the supplier gave nothing for this offer */
	public record OfferAvailability(Long offerId, Availability availability) {
	}

	public record PriceHistoryLine(BigDecimal listPrice, BigDecimal discountRate, BigDecimal netPrice,
			BigDecimal ecoTax, BigDecimal packQuantity, Integer orderingQuantity, String conditionLabel,
			String priceLabel, String salesAgreement, String source, Instant observedAt, String observedBy) {
	}

	public List<ConnectorResponse> connectors() {
		return connectors.values()
			.stream()
			.map(connector -> new ConnectorResponse(connector.supplierCode(), connector.supplierName(),
					connector.available(), connector.environment()))
			.toList();
	}

	/**
	 * Reads again the prices of these offers, except those read recently for
	 * the same quantity. A supplier that fails changes nothing.
	 */
	public List<RefreshedOffer> refresh(List<Long> offerIds, int quantity, Place place, String user) {
		return refresh(sameQuantity(offerIds, quantity), place, user);
	}

	/** The same, with the quantity to buy of each offer. */
	public List<RefreshedOffer> refresh(Map<Long, Integer> quantities, Place place, String user) {
		List<OfferRow> rows = rows(List.copyOf(quantities.keySet()));
		Map<Long, RefreshStatus> statuses = new HashMap<>();
		Map<String, List<OfferRow>> toAsk = new HashMap<>();
		for (OfferRow row : rows) {
			SupplierConnector connector = connected(row);
			if (connector == null) {
				statuses.put(row.id(), RefreshStatus.NOT_CONNECTED);
			}
			else if (isFresh(row, connector, quantities.get(row.id()), place)) {
				statuses.put(row.id(), RefreshStatus.FRESH);
			}
			else {
				toAsk.computeIfAbsent(row.supplierCode(), code -> new ArrayList<>()).add(row);
			}
		}
		toAsk.forEach((code, asked) -> {
			SupplierConnector connector = connectors.get(code);
			List<Product> products = asked.stream().map(row -> row.product(quantities.get(row.id()))).toList();
			Map<String, Price> prices = connector.prices(products, place)
				.stream()
				.collect(Collectors.toMap(Price::key, Function.identity(), (first, second) -> first));
			transaction.executeWithoutResult(status -> asked.forEach(row -> {
				Price price = prices.get(row.key());
				if (price != null) {
					write(row.id(), price, connector.source(), quantities.get(row.id()), user);
				}
				statuses.put(row.id(), price == null ? RefreshStatus.NOT_PRICED : RefreshStatus.UPDATED);
			}));
			log.info("{} prices asked to {} by {}", asked.size(), code, user);
		});
		return rows.stream()
			.map(row -> new RefreshedOffer(row.id(), statuses.get(row.id()), catalog.find(row.id()).orElse(null),
					lastPriceLabel(row.id())))
			.toList();
	}

	private String lastPriceLabel(Long offerId) {
		List<String> labels = jdbc.queryForList("""
				SELECT price_label FROM supplier_offer_price WHERE offer_id = ? ORDER BY observed_at DESC, id DESC LIMIT 1""",
				String.class, offerId);
		return labels.isEmpty() ? null : labels.getFirst();
	}

	/** The stocks of these offers, from the supplier or read in the last minutes. */
	public List<OfferAvailability> availability(List<Long> offerIds, int quantity, Place place) {
		return availability(sameQuantity(offerIds, quantity), place);
	}

	/** The same, with the quantity to buy of each offer. */
	public List<OfferAvailability> availability(Map<Long, Integer> quantities, Place place) {
		List<OfferRow> rows = rows(List.copyOf(quantities.keySet()));
		Instant now = clock.instant();
		Map<Long, Availability> found = new HashMap<>();
		Map<String, List<OfferRow>> toAsk = new HashMap<>();
		for (OfferRow row : rows) {
			SupplierConnector connector = connected(row);
			if (connector == null) {
				continue;
			}
			CachedAvailability cached = availabilities.get(cacheKey(connector, row, quantities.get(row.id()), place));
			if (cached != null && cached.expiresAt().isAfter(now)) {
				found.put(row.id(), cached.availability());
			}
			else {
				toAsk.computeIfAbsent(row.supplierCode(), code -> new ArrayList<>()).add(row);
			}
		}
		toAsk.forEach((code, asked) -> {
			SupplierConnector connector = connectors.get(code);
			Map<String, Availability> read = connector
				.availability(asked.stream().map(row -> row.product(quantities.get(row.id()))).toList(), place)
				.stream()
				.collect(Collectors.toMap(Availability::key, Function.identity(), (first, second) -> first));
			if (availabilities.size() + asked.size() > MAX_CACHED_AVAILABILITIES) {
				// shortcut: the whole cache is dropped when full, a true LRU if this ever hurts.
				availabilities.clear();
			}
			for (OfferRow row : asked) {
				Availability availability = read.get(row.key());
				if (availability != null) {
					found.put(row.id(), availability);
					availabilities.put(cacheKey(connector, row, quantities.get(row.id()), place),
							new CachedAvailability(availability, now.plus(properties.availabilityMaxAge())));
				}
			}
		});
		return rows.stream().map(row -> new OfferAvailability(row.id(), found.get(row.id()))).toList();
	}

	/**
	 * A reference given by hand: the supplier checks it by giving its price, and
	 * the offer exists only then. The offer of the same product is updated.
	 * @param packQuantity units sold for the price of a new offer; null for 1
	 */
	public Offer lookup(String supplierCode, String manufacturerCode, String manufacturerRef, int quantity,
			BigDecimal packQuantity, String unit, Place place, String user) {
		SupplierConnector connector = connectors.get(supplierCode.strip().toUpperCase());
		if (connector == null || !connector.available()) {
			throw new SupplierConnectorException(SupplierConnectorException.Kind.NOT_CONFIGURED);
		}
		String code = manufacturerCode.strip().toUpperCase();
		String ref = manufacturerRef.strip();
		Price price = connector.prices(List.of(new Product(code, ref, quantity)), place)
			.stream()
			.filter(candidate -> candidate.key().equals(SupplierConnector.key(code, ref)))
			.findFirst()
			.orElseThrow(() -> problem(HttpStatus.UNPROCESSABLE_CONTENT,
					"Le fournisseur ne connaît pas cette référence ou ne donne pas de prix pour elle."));
		Long offerId = transaction.execute(status -> save(connector, price, code, ref, quantity, packQuantity, unit, user));
		log.info("Reference {} {} checked at {} by {}", code, ref, connector.supplierCode(), user);
		return catalog.find(offerId).orElseThrow();
	}

	/** An offer of a product the supplier just priced: the one it has, or a new one. */
	private Long save(SupplierConnector connector, Price price, String code, String ref, int quantity,
			BigDecimal packQuantity, String unit, String user) {
		jdbc.update("INSERT INTO supplier (code, name, created_at) VALUES (?, ?, ?) ON CONFLICT (code) DO NOTHING",
				connector.supplierCode(), connector.supplierName(), Timestamp.from(clock.instant()));
		Long supplierId = jdbc.queryForObject("SELECT id FROM supplier WHERE code = ?", Long.class,
				connector.supplierCode());
		List<Long> known = jdbc.queryForList("""
				SELECT id FROM supplier_offer WHERE supplier_id = ? AND upper(manufacturer_code) = ?
				AND upper(manufacturer_ref) = upper(?) ORDER BY id LIMIT 1""", Long.class, supplierId, code, ref);
		if (!known.isEmpty()) {
			write(known.getFirst(), price, connector.source(), quantity, user);
			return known.getFirst();
		}
		Long id = jdbc.queryForObject("""
				INSERT INTO supplier_offer (supplier_id, supplier_ref, manufacturer_code, manufacturer_ref, designation,
				    purchase_unit, pack_quantity, net_price, imported_at, price_source)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (supplier_id, supplier_ref) DO UPDATE SET manufacturer_code = excluded.manufacturer_code,
				    manufacturer_ref = excluded.manufacturer_ref
				RETURNING id""", Long.class, supplierId,
				price.supplierRef() != null ? price.supplierRef() : code + "-" + ref, code, ref,
				price.designation() != null ? price.designation() : ref, unit,
				packQuantity != null ? packQuantity : BigDecimal.ONE, price.netPrice(),
				Timestamp.from(clock.instant()), connector.source());
		write(id, price, connector.source(), quantity, user);
		return id;
	}

	/**
	 * Where the filling of the catalogue stands.
	 * @param products products found in the orders
	 * @param imported those priced by the supplier, now in the catalogue
	 * @param notPriced those it gave no price for, left out
	 * @param error why it stopped, or null
	 */
	public record CatalogueFilling(boolean running, int ordersRead, int ordersFailed, int products, int imported,
			int notPriced, String error, Instant finishedAt) {
	}

	/**
	 * Fills the catalogue from the past orders, in the background: reading
	 * hundreds of orders takes minutes. Asked again, it updates the same offers.
	 */
	public CatalogueFilling startFilling(String supplierCode, String user) {
		String code = supplierCode.strip().toUpperCase();
		SupplierConnector connector = connectors.get(code);
		if (connector == null || !connector.available()) {
			throw new SupplierConnectorException(SupplierConnectorException.Kind.NOT_CONFIGURED);
		}
		boolean[] started = { false };
		CatalogueFilling state = fillings.compute(code, (key, current) -> {
			if (current != null && current.running()) {
				return current;
			}
			started[0] = true;
			return new CatalogueFilling(true, 0, 0, 0, 0, 0, null, null);
		});
		if (started[0]) {
			Thread.ofVirtual().name("catalogue-" + code).start(() -> fill(connector, code, user));
		}
		return state;
	}

	/** @return null when it was never asked since the start of the application */
	public CatalogueFilling filling(String supplierCode) {
		return fillings.get(supplierCode.strip().toUpperCase());
	}

	private void fill(SupplierConnector connector, String code, String user) {
		int read = 0;
		int failed = 0;
		int products = 0;
		int imported = 0;
		int notPriced = 0;
		String error = null;
		try {
			SupplierConnector.Purchases purchases = connector.purchases();
			read = purchases.ordersRead();
			failed = purchases.ordersFailed();
			products = purchases.products().size();
			fillings.put(code, new CatalogueFilling(true, read, failed, products, 0, 0, null, null));
			for (int start = 0; start < products; start += FILLING_CHUNK) {
				List<Product> chunk = purchases.products().subList(start, Math.min(start + FILLING_CHUNK, products));
				Map<String, Price> prices = connector.prices(chunk, new Place(null, null, null))
					.stream()
					.collect(Collectors.toMap(Price::key, Function.identity(), (first, second) -> first));
				int saved = transaction.execute(status -> {
					int count = 0;
					for (Product product : chunk) {
						Price price = prices.get(product.key());
						if (price != null) {
							save(connector, price, product.manufacturerCode().toUpperCase(), product.manufacturerRef(), 1,
									null, null, user);
							count++;
						}
					}
					return count;
				});
				imported += saved;
				notPriced += chunk.size() - saved;
				fillings.put(code, new CatalogueFilling(true, read, failed, products, imported, notPriced, null, null));
			}
		}
		catch (SupplierConnectorException ex) {
			error = ex.getBody().getDetail();
		}
		catch (RuntimeException ex) {
			log.error("Catalogue of {} not filled", code, ex);
			error = "Remplissage interrompu : voir le journal du serveur.";
		}
		fillings.put(code, new CatalogueFilling(false, read, failed, products, imported, notPriced, error, clock.instant()));
		log.info("Catalogue of {}: {} orders read, {} products found, {} kept, by {}", code, read, products, imported, user);
	}

	/** The prices of an offer, the last first. */
	public List<PriceHistoryLine> history(Long offerId) {
		return jdbc.query("""
				SELECT * FROM supplier_offer_price WHERE offer_id = ? ORDER BY observed_at DESC, id DESC LIMIT 200""",
				(row, number) -> new PriceHistoryLine(row.getBigDecimal("list_price"), row.getBigDecimal("discount_rate"),
						row.getBigDecimal("net_price"), row.getBigDecimal("eco_tax"), row.getBigDecimal("pack_quantity"),
						row.getObject("ordering_quantity", Integer.class), row.getString("condition_label"),
						row.getString("price_label"), row.getString("sales_agreement"), row.getString("source"),
						row.getTimestamp("observed_at").toInstant(), row.getString("observed_by")),
				offerId);
	}

	/**
	 * The dates of validity go: a price read today is dated by the time of its
	 * reading, which the estimates copy as the date of their cost.
	 */
	private void write(Long offerId, Price price, String source, int quantity, String user) {
		Timestamp now = Timestamp.from(clock.instant());
		jdbc.update("""
				UPDATE supplier_offer SET list_price = ?, discount_rate = ?, net_price = ?, eco_tax = ?, price_source = ?,
				    priced_quantity = ?, valid_from = NULL, valid_until = NULL, imported_at = ?, imported_by = ?
				WHERE id = ?""", price.listPrice(), price.discountRate(), price.netPrice(), price.ecoTax(), source,
				quantity, now, user, offerId);
		// A line only when something changed: an unchanged price asked every day adds nothing.
		jdbc.update("""
				INSERT INTO supplier_offer_price (offer_id, list_price, discount_rate, net_price, eco_tax, pack_quantity,
				    ordering_quantity, condition_label, price_label, sales_agreement, source, observed_at, observed_by)
				SELECT o.id, o.list_price, o.discount_rate, o.net_price, o.eco_tax, o.pack_quantity, o.priced_quantity,
				    CAST(? AS text), CAST(? AS text), CAST(? AS varchar), o.price_source, o.imported_at, o.imported_by
				FROM supplier_offer o
				LEFT JOIN LATERAL (SELECT * FROM supplier_offer_price p WHERE p.offer_id = o.id
				    ORDER BY p.observed_at DESC, p.id DESC LIMIT 1) last ON TRUE
				WHERE o.id = ? AND (last.id IS NULL OR last.net_price IS DISTINCT FROM o.net_price
				    OR last.list_price IS DISTINCT FROM o.list_price OR last.discount_rate IS DISTINCT FROM o.discount_rate
				    OR last.eco_tax IS DISTINCT FROM o.eco_tax OR last.pack_quantity IS DISTINCT FROM o.pack_quantity
				    OR last.condition_label IS DISTINCT FROM CAST(? AS text)
				    OR last.price_label IS DISTINCT FROM CAST(? AS text)
				    OR last.sales_agreement IS DISTINCT FROM CAST(? AS varchar))""", price.conditionLabel(),
				price.priceLabel(), cut(price.salesAgreement()), offerId, price.conditionLabel(), price.priceLabel(),
				cut(price.salesAgreement()));
	}

	private static Map<Long, Integer> sameQuantity(List<Long> offerIds, int quantity) {
		return offerIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> quantity));
	}

	private boolean isFresh(OfferRow row, SupplierConnector connector, int quantity, Place place) {
		return place.isEmpty() && connector.source().equals(row.priceSource())
				&& row.pricedQuantity() != null && row.pricedQuantity() == quantity
				&& row.pricedAt().plus(properties.priceMaxAge()).isAfter(clock.instant());
	}

	private SupplierConnector connected(OfferRow row) {
		SupplierConnector connector = connectors.get(row.supplierCode());
		return connector != null && connector.available() && row.manufacturerCode() != null
				&& row.manufacturerRef() != null ? connector : null;
	}

	private List<OfferRow> rows(List<Long> offerIds) {
		if (offerIds.isEmpty()) {
			return List.of();
		}
		String marks = String.join(", ", Collections.nCopies(offerIds.size(), "?"));
		return jdbc.query("""
				SELECT o.id, s.code, o.manufacturer_code, o.manufacturer_ref, o.price_source, o.priced_quantity, o.imported_at
				FROM supplier_offer o JOIN supplier s ON s.id = o.supplier_id
				WHERE o.id IN (%s) ORDER BY o.id""".formatted(marks),
				(row, number) -> new OfferRow(row.getLong("id"), row.getString("code"), row.getString("manufacturer_code"),
						row.getString("manufacturer_ref"), row.getString("price_source"),
						row.getObject("priced_quantity", Integer.class), row.getTimestamp("imported_at").toInstant()),
				offerIds.toArray());
	}

	private static String cacheKey(SupplierConnector connector, OfferRow row, int quantity, Place place) {
		return String.join("|", connector.source(), row.key(), String.valueOf(quantity),
				String.valueOf(place.agencyCode()), String.valueOf(place.zipCode()), String.valueOf(place.city()));
	}

	private static String cut(String text) {
		return text == null || text.length() <= 100 ? text : text.substring(0, 100);
	}

	private static ErrorResponseException problem(HttpStatus status, String detail) {
		return new ErrorResponseException(status, ProblemDetail.forStatusAndDetail(status, detail), null);
	}

	private record OfferRow(Long id, String supplierCode, String manufacturerCode, String manufacturerRef,
			String priceSource, Integer pricedQuantity, Instant pricedAt) {

		String key() {
			return SupplierConnector.key(manufacturerCode, manufacturerRef);
		}

		Product product(int quantity) {
			return new Product(manufacturerCode, manufacturerRef, quantity);
		}

	}

	private record CachedAvailability(Availability availability, Instant expiresAt) {
	}

}
