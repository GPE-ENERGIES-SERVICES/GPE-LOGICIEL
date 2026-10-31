package eu.gpefrance.tenders.analysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.analysis.PriceCheck.Issue;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.DocumentPageRepository;
import eu.gpefrance.tenders.document.DocumentProcessor;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.PdfTable;
import eu.gpefrance.tenders.document.PdfTableExtractor;
import eu.gpefrance.tenders.document.PdfTableText;
import eu.gpefrance.tenders.document.SpreadsheetExtractor;

/**
 * Checks the price schedules of a dossier with plain code. Spreadsheets are
 * read again, with the same extractor as for their text, so that sums and
 * comparisons are made on the cells themselves. In a PDF the tables are
 * rebuilt from the position of the words (those of the PDF, or those read by
 * OCR on a scanned page), then checked the same way; a table whose structure
 * is ambiguous, or read by OCR, says so first.
 */
@Component
public class PriceSchedules {

	private static final Logger log = LoggerFactory.getLogger(PriceSchedules.class);

	private final FileStorage storage;

	private final SpreadsheetExtractor extractor;

	private final PdfTableExtractor pdfTables;

	private final DocumentPageRepository pages;

	public PriceSchedules(FileStorage storage, SpreadsheetExtractor extractor, PdfTableExtractor pdfTables,
			DocumentPageRepository pages) {
		this.storage = storage;
		this.extractor = extractor;
		this.pdfTables = pdfTables;
		this.pages = pages;
	}

	/**
	 * @param checks the checks of every sheet and of every table rebuilt
	 * @param tables the tables rebuilt from the PDF, written for the model,
	 * with what could not be rebuilt
	 */
	record Checked(List<PriceCheck> checks, String tables) {
	}

	/**
	 * @param documents the price schedules sent to the model
	 */
	Checked check(List<Document> documents) {
		List<PriceCheck> checks = new ArrayList<>();
		StringBuilder tables = new StringBuilder();
		for (Document document : documents) {
			try {
				if (SpreadsheetExtractor.handles(document.getFileName())) {
					checks.addAll(PriceScheduleChecker.check(document.getId(), document.getFileName(),
							extractor.read(storage.resolve(document.getStoragePath()),
									DocumentProcessor.extension(document.getFileName()))));
				}
				else if (document.getPdfPath() != null) {
					// Pages read by OCR give their stored words, the others those of the PDF.
					List<PdfTable> found = pdfTables.extract(storage.resolve(document.getPdfPath()),
							pages.findByDocumentIdOrderByPageNumber(document.getId()));
					for (int i = 0; i < found.size(); i++) {
						checks.add(check(document, found.get(i), i + 1));
					}
					tables.append(found.isEmpty()
							? "Aucun tableau reconstruit dans le document " + document.getId()
									+ " : en-tête de colonnes non reconnu ou pages sans texte (scannées).\n"
							: PdfTableText.render(document.getId(), document.getFileName(), found));
				}
			}
			catch (IOException ex) {
				// Its name and content are never logged: only which document and why.
				log.warn("Price schedule {} could not be read again: {}", document.getId(),
						ex.getClass().getSimpleName());
			}
		}
		return new Checked(checks, tables.isEmpty() ? "" : "<tableaux_pdf>\n" + tables + "</tableaux_pdf>\n");
	}

	/**
	 * The checks of a table of a PDF, each issue with its page, and the
	 * ambiguity of the table first when there is one.
	 */
	private static PriceCheck check(Document document, PdfTable table, int number) {
		PriceCheck check = PriceScheduleChecker.check(document.getId(), document.getFileName(),
				PdfTableText.sheet(table, number));
		List<Issue> issues = new ArrayList<>();
		if (!table.reliable()) {
			issues.add(new Issue("AMBIGUOUS_LAYOUT", "Structure du tableau ambiguë, contrôles à confirmer : "
					+ String.join(" ; ", table.problems().subList(0, Math.min(3, table.problems().size()))), null,
					null));
		}
		for (Issue issue : check.issues()) {
			Integer page = issue.row() == null ? null : PdfTableText.page(table, issue.row());
			issues.add(page == null ? issue
					: new Issue(issue.kind(), issue.message() + " (page " + page + ")", issue.ref(), issue.row()));
		}
		return new PriceCheck(check.documentId(), check.fileName(), check.sheet(), check.columns(), check.headerRow(),
				check.items(), check.withQuantity(), check.withUnitPrice(), check.withAmount(), check.textPrices(),
				check.computedTotal(), check.amountBasis(), check.blankPrices(), check.totals(), List.copyOf(issues),
				check.moreIssues());
	}

}
