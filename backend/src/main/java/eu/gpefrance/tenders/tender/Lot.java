package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "lot")
public class Lot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tender_id")
	private Tender tender;

	@Column(nullable = false)
	private int position;

	@Column(nullable = false)
	private String number;

	private String title;

	@Column(name = "estimated_amount")
	private BigDecimal estimatedAmount;

	protected Lot() {
	}

	Lot(Tender tender, int position, LotDetails details) {
		this.tender = tender;
		this.position = position;
		this.number = details.number();
		this.title = details.title();
		this.estimatedAmount = details.estimatedAmount();
	}

	public Long getId() {
		return id;
	}

	public int getPosition() {
		return position;
	}

	public String getNumber() {
		return number;
	}

	public String getTitle() {
		return title;
	}

	public BigDecimal getEstimatedAmount() {
		return estimatedAmount;
	}

}
