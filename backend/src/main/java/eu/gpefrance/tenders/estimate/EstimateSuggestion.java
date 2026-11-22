package eu.gpefrance.tenders.estimate;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A proposal of the AI on an estimate. It changes nothing until a user
 * accepts it; an offer accepted is costed with the price of the catalogue,
 * never with a price of the AI.
 */
@Entity
@Table(name = "estimate_suggestion")
public class EstimateSuggestion {

	public enum Kind {

		/** An offer of the catalogues for an item, with its quantity per unit. */
		SUPPLY,

		/** A unit price of the price library for an item, updated to this year. */
		PRICE,

		/** A labour time for an item, in hours per unit, counted at a rate of GPE. */
		LABOR,

		/** A point to check. */
		CHECK

	}

	public enum Status {

		PENDING, ACCEPTED, REJECTED

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "estimate_id", nullable = false, updatable = false)
	private Long estimateId;

	@Column(name = "item_id", updatable = false)
	private Long itemId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private Kind kind;

	@Column(name = "offer_id", updatable = false)
	private Long offerId;

	@Column(updatable = false)
	private BigDecimal quantity;

	@Column(name = "library_item_id", updatable = false)
	private Long libraryItemId;

	/** The rate of GPE a labour time is counted at (LABOR). */
	@Column(name = "rate_code", updatable = false)
	private String rateCode;

	/** The price proposed (PRICE), already updated. */
	@Column(updatable = false)
	private BigDecimal price;

	@Column(nullable = false, updatable = false)
	private String text;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.PENDING;

	@Column(updatable = false)
	private String model;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "decided_by")
	private String decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected EstimateSuggestion() {
	}

	EstimateSuggestion(Long estimateId, Long itemId, Kind kind, Long offerId, BigDecimal quantity, String text,
			String model, Instant now) {
		this.estimateId = estimateId;
		this.itemId = itemId;
		this.kind = kind;
		this.offerId = offerId;
		this.quantity = quantity;
		this.text = text;
		this.model = model;
		this.createdAt = now;
	}

	/** A labour time for an item: hours per unit at a rate of GPE. */
	static EstimateSuggestion labor(Long estimateId, Long itemId, String rateCode, BigDecimal hours, String text,
			String model, Instant now) {
		EstimateSuggestion suggestion = new EstimateSuggestion(estimateId, itemId, Kind.LABOR, null, hours, text, model, now);
		suggestion.rateCode = rateCode;
		return suggestion;
	}

	/** A price of the library for an item. */
	static EstimateSuggestion price(Long estimateId, Long itemId, Long libraryItemId, BigDecimal price, String text,
			String model, Instant now) {
		EstimateSuggestion suggestion = new EstimateSuggestion(estimateId, itemId, Kind.PRICE, null, null, text, model,
				now);
		suggestion.libraryItemId = libraryItemId;
		suggestion.price = price;
		return suggestion;
	}

	void decide(Status status, String user, Instant now) {
		this.status = status;
		this.decidedBy = user;
		this.decidedAt = now;
	}

	public record Response(Long id, Long itemId, Kind kind, Long offerId, BigDecimal quantity, String text,
			Status status, String model, Instant createdAt, String decidedBy, Instant decidedAt, Long libraryItemId,
			BigDecimal price, String rateCode) {
	}

	Response response() {
		return new Response(id, itemId, kind, offerId, quantity, text, status, model, createdAt, decidedBy, decidedAt,
				libraryItemId, price, rateCode);
	}

	public Long getId() {
		return id;
	}

	public Long getEstimateId() {
		return estimateId;
	}

	public Long getItemId() {
		return itemId;
	}

	public Kind getKind() {
		return kind;
	}

	public Long getOfferId() {
		return offerId;
	}

	public Long getLibraryItemId() {
		return libraryItemId;
	}

	public String getRateCode() {
		return rateCode;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public String getText() {
		return text;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public Status getStatus() {
		return status;
	}

}
