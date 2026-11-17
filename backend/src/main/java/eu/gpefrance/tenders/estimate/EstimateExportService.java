package eu.gpefrance.tenders.estimate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * An estimate written as the costing table of GPE: the sheet « Calcul P.V. »
 * (supplies, subcontracting and labour of each price, then the fees and the
 * net margin), the sheet « Fiche budget » (margins of the whole job), then
 * the costs of each item. Every figure is the one computed by the
 * application, written as a value, not as a formula.
 */
@Service
public class EstimateExportService {

	/** The three blocks of costs of the GPE table. */
	enum Family {

		SUPPLY, SUBCONTRACT, LABOR;

		static Family of(ComponentType type) {
			return switch (type) {
				case SUBCONTRACT, SERVICE -> SUBCONTRACT;
				case LABOR, TRAVEL -> LABOR;
				case SUPPLY, RENTAL, LUMP_SUM, OTHER -> SUPPLY;
			};
		}

	}

	/**
	 * The costs of one unit of an item in one block; cost and sale null when a
	 * unit cost of the block is missing.
	 * @param hours quantities of the labour components, in hours
	 */
	record Block(BigDecimal cost, BigDecimal sale, BigDecimal hours) {

		BigDecimal rate() {
			return cost == null || cost.signum() == 0 ? null
					: sale.divide(cost, 4, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
		}

	}

	private static final String[] PRICE_COLUMNS = { "N°", "Désignation du prix", "U", "QT", "P.U. HT",
			"Total HT", "%", "Achat", "Coef.", "Vente", "Déboursé total", "Achat", "Coef.", "Vente", "Déboursé total",
			"Taux horaire", "Heures", "Achat", "Coef.", "Vente", "Heures totales", "Déboursé total", "Prix de vente",
			"Frais agence", "Frais généraux", "Frais service", "Marge nette", "% marge" };

	private static final String[] COMPONENT_COLUMNS = { "Poste", "Désignation du poste", "Type", "Libellé",
			"Quantité par unité", "Unité", "Coût unitaire", "Perte", "Coefficient", "Fournisseur", "Réf. fournisseur",
			"Réf. fabricant", "Date du prix", "Taux GPE" };

	private static final String[] TYPE_LABELS = { "Fourniture", "Main-d'œuvre", "Prestation", "Déplacement",
			"Location", "Sous-traitance", "Forfait", "Divers" };

	/** First column of each block in « Calcul P.V. ». */
	private static final Map<Family, Integer> BLOCK_COLUMNS = Map.of(Family.SUPPLY, 7, Family.SUBCONTRACT, 11,
			Family.LABOR, 15);

	private final EstimateService estimates;

	private final TenderRepository tenders;

	private final PricingSettingsRepository settings;

	public EstimateExportService(EstimateService estimates, TenderRepository tenders,
			PricingSettingsRepository settings) {
		this.estimates = estimates;
		this.tenders = tenders;
		this.settings = settings;
	}

	public record Export(String fileName, byte[] content) {
	}

	private record Styles(CellStyle bold, CellStyle money, CellStyle percent, CellStyle group) {
	}

	@Transactional(readOnly = true)
	public Export export(Long id) {
		Estimate estimate = estimates.found(id);
		String title = tenders.findById(estimate.getTenderId()).map(tender -> tender.getTitle()).orElse("");
		// shortcut: fee rates are those in force at the export, not copied in the estimate; copy them if a past export must be reproduced exactly
		PricingSettings fees = settings.current();
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Styles styles = styles(workbook);
			Totals totals = prices(workbook.createSheet("Calcul P.V."), estimate, title, fees, styles);
			budget(workbook.createSheet("Fiche budget"), estimate, title, fees, totals, styles);
			components(workbook.createSheet("Sous-détail"), estimate, styles);
			workbook.write(out);
			return new Export("chiffrage-" + estimate.getTenderId() + "-v" + estimate.getVersion() + ".xlsx",
					out.toByteArray());
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** Sums over the items out of option, a block total null when one of its costs is missing. */
	private static final class Totals {

		final Map<Family, BigDecimal> costs = new EnumMap<>(Family.class);

		BigDecimal hours = BigDecimal.ZERO;

		Totals() {
			for (Family family : Family.values()) {
				costs.put(family, BigDecimal.ZERO);
			}
		}

		void add(Family family, Block block, BigDecimal quantity) {
			BigDecimal sum = costs.get(family);
			costs.put(family, sum == null || block.cost() == null || quantity == null ? null
					: sum.add(block.cost().multiply(quantity)));
			if (quantity != null) {
				hours = hours.add(block.hours().multiply(quantity));
			}
		}

	}

	private static Totals prices(Sheet sheet, Estimate estimate, String title, PricingSettings fees, Styles styles) {
		int r = 0;
		Row head = sheet.createRow(r++);
		text(head, 0, "Nom de l'affaire :", styles.bold());
		text(head, 1, title, styles.bold());
		text(sheet.createRow(r++), 1, "Version " + estimate.getVersion() + " — " + status(estimate)
				+ (estimate.getLotNumber() == null ? "" : " — lot " + estimate.getLotNumber())
				+ (estimate.getLabel() == null ? "" : " — " + estimate.getLabel()), null);
		text(sheet.createRow(r++), 1, "Frais sur prix de vente : agence " + percent(fees.getAgencyFeeRate())
				+ ", généraux " + percent(fees.getOverheadRate()) + ", service " + percent(fees.getServiceFeeRate())
				+ " (paramètres en vigueur à l'export)", null);
		Row groups = sheet.createRow(r);
		group(sheet, groups, r, 7, 10, "FOURNITURES", styles);
		group(sheet, groups, r, 11, 14, "SOUS-TRAITANTS", styles);
		group(sheet, groups, r, 15, 21, "MAIN D'ŒUVRE", styles);
		group(sheet, groups, r, 22, 27, "RÉSULTATS", styles);
		r++;
		header(sheet.createRow(r++), PRICE_COLUMNS, styles.bold());
		sheet.createFreezePane(2, r);

		BigDecimal totalHt = estimate.getTotalHt();
		Totals totals = new Totals();
		String chapter = null;
		for (EstimateItem item : estimate.getItems()) {
			if (item.getChapter() != null && !item.getChapter().equals(chapter)) {
				chapter = item.getChapter();
				text(sheet.createRow(r++), 1, chapter, styles.bold());
			}
			Row row = sheet.createRow(r++);
			BigDecimal quantity = item.getQuantity();
			text(row, 0, item.getReference(), null);
			text(row, 1, item.getDesignation() + (item.isOption() ? " (option)" : ""), null);
			text(row, 2, item.getUnit(), null);
			number(row, 3, quantity, null);
			if (item.getPriceMode() == PriceMode.TEXT) {
				text(row, 4, item.getTextPrice(), null);
			}
			else {
				number(row, 4, item.getUnitPrice(), styles.money());
			}
			number(row, 5, item.getAmount(), styles.money());
			if (!item.isOption() && item.getAmount() != null && totalHt.signum() != 0) {
				number(row, 6, item.getAmount().divide(totalHt, 4, RoundingMode.HALF_UP), styles.percent());
			}
			Map<Family, Block> blocks = blocks(item, estimate.getCoefficients());
			blocks.forEach((family, block) -> {
				int c = BLOCK_COLUMNS.get(family);
				BigDecimal total = block.cost() == null || quantity == null ? null : block.cost().multiply(quantity);
				if (family == Family.LABOR) {
					if (block.hours().signum() != 0) {
						number(row, c, block.cost() == null ? null
								: block.cost().divide(block.hours(), 2, RoundingMode.HALF_UP), styles.money());
						number(row, c + 1, block.hours(), null);
						number(row, c + 5, quantity == null ? null : block.hours().multiply(quantity), null);
					}
					c += 2;
				}
				number(row, c, block.cost(), styles.money());
				number(row, c + 1, block.rate(), styles.percent());
				number(row, c + 2, block.sale(), styles.money());
				number(row, family == Family.LABOR ? c + 4 : c + 3, total, styles.money());
				if (!item.isOption()) {
					totals.add(family, block, quantity);
				}
			});
			BigDecimal price = item.getUnitPrice();
			if (price != null && !blocks.isEmpty()) {
				number(row, 22, price, styles.money());
				BigDecimal agency = cents(price.multiply(fees.getAgencyFeeRate()));
				BigDecimal overhead = cents(price.multiply(fees.getOverheadRate()));
				BigDecimal service = cents(price.multiply(fees.getServiceFeeRate()));
				number(row, 23, agency, styles.money());
				number(row, 24, overhead, styles.money());
				number(row, 25, service, styles.money());
				if (item.getUnitCost() != null) {
					BigDecimal margin = price.subtract(item.getUnitCost()).subtract(agency).subtract(overhead)
						.subtract(service);
					number(row, 26, cents(margin), styles.money());
					if (price.signum() != 0) {
						number(row, 27, margin.divide(price, 4, RoundingMode.HALF_UP), styles.percent());
					}
				}
			}
		}
		r++;
		Row sums = sheet.createRow(r);
		text(sums, 4, "Total HT", styles.bold());
		number(sums, 5, totalHt, styles.money());
		number(sums, 10, totals.costs.get(Family.SUPPLY), styles.money());
		number(sums, 14, totals.costs.get(Family.SUBCONTRACT), styles.money());
		number(sums, 20, totals.hours, null);
		number(sums, 21, totals.costs.get(Family.LABOR), styles.money());
		r++;
		r = total(sheet, r, "TVA " + percent(estimate.getVatRate()), estimate.getTotalVat(), styles);
		r = total(sheet, r, "Total TTC", estimate.getTotalTtc(), styles);
		if (estimate.getOptionsHt().signum() != 0) {
			total(sheet, r, "Options HT (hors total)", estimate.getOptionsHt(), styles);
		}
		widths(sheet, 8, 60, 6, 8, 12, 14, 8, 12, 8, 12, 14, 12, 8, 12, 14, 12, 8, 12, 8, 12, 12, 14, 14, 12, 12, 12,
				14, 9);
		return totals;
	}

	private static void budget(Sheet sheet, Estimate estimate, String title, PricingSettings fees, Totals totals,
			Styles styles) {
		BigDecimal sale = estimate.getTotalHt();
		BigDecimal supply = totals.costs.get(Family.SUPPLY);
		BigDecimal subcontract = totals.costs.get(Family.SUBCONTRACT);
		BigDecimal labor = totals.costs.get(Family.LABOR);
		BigDecimal purchases = sum(supply, subcontract);
		// An item priced without its costs leaves the costs short: no margin rather than a false one.
		boolean complete = estimate.getTotalCost() != null;
		BigDecimal contribution = purchases == null || !complete ? null : sale.subtract(purchases);
		BigDecimal gross = contribution == null || labor == null ? null : contribution.subtract(labor);
		BigDecimal agency = cents(sale.multiply(fees.getAgencyFeeRate()));
		BigDecimal overhead = cents(sale.multiply(fees.getOverheadRate()));
		BigDecimal service = cents(sale.multiply(fees.getServiceFeeRate()));
		BigDecimal net = gross == null ? null : gross.subtract(agency).subtract(overhead).subtract(service);

		int r = 0;
		text(sheet.createRow(r++), 0, "BUDGET AFFAIRE", styles.bold());
		r++;
		Row name = sheet.createRow(r++);
		text(name, 0, "Nom de l'affaire :", null);
		text(name, 1, title, styles.bold());
		Row version = sheet.createRow(r++);
		text(version, 0, "Chiffrage :", null);
		text(version, 1, "version " + estimate.getVersion() + " — " + status(estimate)
				+ (estimate.getLotNumber() == null ? "" : " — lot " + estimate.getLotNumber()), null);
		r++;
		r = line(sheet, r, "PRIX DE VENTE H.T. (A)", sale, null, styles, true);
		r++;
		text(sheet.createRow(r++), 0, "Achats et sous-traitance", styles.bold());
		r = line(sheet, r, "Fournitures", supply, null, styles, false);
		r = line(sheet, r, "Sous-traitance", subcontract, null, styles, false);
		r = line(sheet, r, "Sous-total (1)", purchases, null, styles, true);
		r++;
		r = line(sheet, r, "MARGE CONTRIBUTIVE", contribution, ratio(contribution, sale), styles, true);
		r++;
		text(sheet.createRow(r++), 0, "Production main-d'œuvre", styles.bold());
		Row hours = sheet.createRow(r++);
		text(hours, 0, "Heures", null);
		number(hours, 1, totals.hours, null);
		r = line(sheet, r, "Sous-total (2)", labor, null, styles, true);
		r++;
		r = line(sheet, r, "MARGE BRUTE", gross, ratio(gross, sale), styles, true);
		r++;
		r = line(sheet, r, "Frais d'agence (" + percent(fees.getAgencyFeeRate()) + ")", agency, null, styles, false);
		r = line(sheet, r, "Frais généraux (" + percent(fees.getOverheadRate()) + ")", overhead, null, styles, false);
		r = line(sheet, r, "Frais de service (" + percent(fees.getServiceFeeRate()) + ")", service, null, styles,
				false);
		r++;
		r = line(sheet, r, "MARGE NETTE (B)", net, ratio(net, sale), styles, true);
		if (!complete) {
			r++;
			text(sheet.createRow(r), 0, "Chiffrage incomplet : un coût manque, les marges ne sont pas calculées.",
					styles.bold());
		}
		widths(sheet, 40, 16, 10);
	}

	private static void components(Sheet sheet, Estimate estimate, Styles styles) {
		int r = 0;
		header(sheet.createRow(r++), COMPONENT_COLUMNS, styles.bold());
		for (EstimateItem item : estimate.getItems()) {
			for (EstimateComponent component : item.getComponents()) {
				Row row = sheet.createRow(r++);
				text(row, 0, item.getReference() == null ? String.valueOf(item.getPosition()) : item.getReference(),
						null);
				text(row, 1, item.getDesignation(), null);
				text(row, 2, TYPE_LABELS[component.getType().ordinal()], null);
				text(row, 3, component.getLabel(), null);
				number(row, 4, component.getQuantity(), null);
				text(row, 5, component.getUnit(), null);
				number(row, 6, component.getUnitCost(), null);
				number(row, 7, component.getWasteRate(), null);
				number(row, 8, component.cost().coefficient(estimate.getCoefficients()), null);
				text(row, 9, component.getSupplierName(), null);
				text(row, 10, component.getSupplierRef(), null);
				text(row, 11, component.getManufacturerRef(), null);
				text(row, 12, component.getCostDate() == null ? null : component.getCostDate().toString(), null);
				text(row, 13, component.getRateCode(), null);
			}
		}
		widths(sheet, 10, 40, 14, 40, 12, 8, 14, 8, 10, 16, 16, 16, 12, 10);
	}

	/** The blocks an item has costs in, by the calculator's own formulas. */
	static Map<Family, Block> blocks(EstimateItem item, Map<ComponentType, BigDecimal> coefficients) {
		Map<Family, Block> blocks = new EnumMap<>(Family.class);
		for (EstimateComponent component : item.getComponents()) {
			EstimateCalculator.Cost cost = component.cost();
			BigDecimal value = cost.value();
			BigDecimal hours = component.getType() == ComponentType.LABOR ? cost.quantity() : BigDecimal.ZERO;
			Block block = new Block(value, value == null ? null : value.multiply(cost.coefficient(coefficients)),
					hours);
			blocks.merge(Family.of(component.getType()), block,
					(a, b) -> new Block(sum(a.cost(), b.cost()), sum(a.sale(), b.sale()), a.hours().add(b.hours())));
		}
		return blocks;
	}

	private static BigDecimal sum(BigDecimal a, BigDecimal b) {
		return a == null || b == null ? null : a.add(b);
	}

	private static BigDecimal ratio(BigDecimal value, BigDecimal sale) {
		return value == null || sale.signum() == 0 ? null : value.divide(sale, 4, RoundingMode.HALF_UP);
	}

	private static BigDecimal cents(BigDecimal value) {
		return EstimateCalculator.cents(value);
	}

	private static String percent(BigDecimal rate) {
		return rate.movePointRight(2).stripTrailingZeros().toPlainString() + " %";
	}

	private static String status(Estimate estimate) {
		return switch (estimate.getStatus()) {
			case DRAFT -> "brouillon";
			case VALIDATED -> "validée par " + estimate.getValidatedBy();
			case SUPERSEDED -> "remplacée";
		};
	}

	private static Styles styles(XSSFWorkbook workbook) {
		Font font = workbook.createFont();
		font.setBold(true);
		CellStyle bold = workbook.createCellStyle();
		bold.setFont(font);
		CellStyle money = workbook.createCellStyle();
		money.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
		CellStyle percent = workbook.createCellStyle();
		percent.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
		CellStyle group = workbook.createCellStyle();
		group.setFont(font);
		group.setAlignment(HorizontalAlignment.CENTER);
		return new Styles(bold, money, percent, group);
	}

	private static void group(Sheet sheet, Row row, int r, int first, int last, String label, Styles styles) {
		text(row, first, label, styles.group());
		sheet.addMergedRegion(new CellRangeAddress(r, r, first, last));
	}

	private static int line(Sheet sheet, int r, String label, BigDecimal value, BigDecimal ratio, Styles styles,
			boolean bold) {
		Row row = sheet.createRow(r);
		text(row, 0, label, bold ? styles.bold() : null);
		number(row, 1, value, styles.money());
		number(row, 2, ratio, styles.percent());
		return r + 1;
	}

	private static int total(Sheet sheet, int r, String label, BigDecimal value, Styles styles) {
		Row row = sheet.createRow(r);
		text(row, 4, label, styles.bold());
		number(row, 5, value, styles.money());
		return r + 1;
	}

	private static void header(Row row, String[] columns, CellStyle bold) {
		for (int i = 0; i < columns.length; i++) {
			text(row, i, columns[i], bold);
		}
	}

	private static void text(Row row, int column, String value, CellStyle style) {
		if (value != null) {
			row.createCell(column).setCellValue(value);
			if (style != null) {
				row.getCell(column).setCellStyle(style);
			}
		}
	}

	private static void number(Row row, int column, BigDecimal value, CellStyle style) {
		if (value != null) {
			row.createCell(column).setCellValue(value.doubleValue());
			if (style != null) {
				row.getCell(column).setCellStyle(style);
			}
		}
	}

	private static void widths(Sheet sheet, int... characters) {
		for (int i = 0; i < characters.length; i++) {
			sheet.setColumnWidth(i, characters[i] * 256);
		}
	}

}
