package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Who a tender, or one of its lots, was awarded to: the company when won,
 * another one when its offer was rejected. One per tender and lot: recording
 * it again updates it, so an award notice read twice never makes two.
 */
@Entity
@Table(name = "tender_award")
public class TenderAward {

	public enum Source {

		/** Read in the published award notice. */
		NOTICE,

		/** Confirmed by a user. */
		MANUAL

	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tender_id", updatable = false)
	private Tender tender;

	/** Null for a contract without lots. */
	@Column(name = "lot_number", updatable = false)
	private String lotNumber;

	@Column(nullable = false)
	private String winner;

	private BigDecimal amount;

	@Column(name = "awarded_at", nullable = false)
	private LocalDate awardedAt;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private Source source;

	@Column(nullable = false)
	private boolean won;

	@Column(name = "recorded_by")
	private String recordedBy;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	protected TenderAward() {
	}

	TenderAward(Tender tender, String lotNumber) {
		this.tender = tender;
		this.lotNumber = lotNumber;
	}

	void record(String winner, BigDecimal amount, LocalDate awardedAt, Source source, boolean won, String user,
			Instant now) {
		this.winner = winner;
		this.amount = amount;
		this.awardedAt = awardedAt;
		this.source = source;
		this.won = won;
		this.recordedBy = user;
		this.recordedAt = now;
	}

	public Long getId() {
		return id;
	}

	public Tender getTender() {
		return tender;
	}

	public String getLotNumber() {
		return lotNumber;
	}

	public String getWinner() {
		return winner;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public LocalDate getAwardedAt() {
		return awardedAt;
	}

	public Source getSource() {
		return source;
	}

	public boolean isWon() {
		return won;
	}

}
