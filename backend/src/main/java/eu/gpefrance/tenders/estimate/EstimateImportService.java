package eu.gpefrance.tenders.estimate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.analysis.PriceScheduleChecker;
import eu.gpefrance.tenders.analysis.PriceScheduleChecker.ScheduleLine;
import eu.gpefrance.tenders.document.Document;
import eu.gpefrance.tenders.document.DocumentPageRepository;
import eu.gpefrance.tenders.document.DocumentProcessor;
import eu.gpefrance.tenders.document.DocumentRepository;
import eu.gpefrance.tenders.document.FileStorage;
import eu.gpefrance.tenders.document.PdfTable;
import eu.gpefrance.tenders.document.PdfTableExtractor;
import eu.gpefrance.tenders.document.PdfTableText;
import eu.gpefrance.tenders.document.Spreadsheet.Sheet;
import eu.gpefrance.tenders.document.SpreadsheetExtractor;
import eu.gpefrance.tenders.estimate.EstimateEvent.Kind;

/**
 * Adds to a draft the lines of work of a price schedule (DPGF, BPU, DQE) of
 * its tender, read by plain code: reference, designation, unit, quantity and
 * chapter, each item with the place it comes from. The prices of the file are
 * those of the buyer and are never read: each item waits for its price,
 * entered by a user or computed from its costs.
 * The lines of a PDF, rebuilt from the position of the words, and the lines
 * not read in full are marked to be checked.
 */
@Service
public class EstimateImportService {

	static final int MAX_ITEMS = 5000;

	private static final Logger log = LoggerFactory.getLogger(EstimateImportService.class);

	private final EstimateService estimates;

	private final DocumentRepository documents;

	private final DocumentPageRepository pages;

	private final FileStorage storage;

	private final SpreadsheetExtractor spreadsheets;

	private final PdfTableExtractor pdfTables;

	public EstimateImportService(EstimateService estimates, DocumentRepository documents, DocumentPageRepository pages,
			FileStorage storage, SpreadsheetExtractor spreadsheets, PdfTableExtractor pdfTables) {
		this.estimates = estimates;
		this.documents = documents;
		this.pages = pages;
		this.storage = storage;
		this.spreadsheets = spreadsheets;
		this.pdfTables = pdfTables;
	}

	@Transactional
	public EstimateResponse importDocument(Long estimateId, Long documentId, String user) {
		Estimate estimate = estimates.found(estimateId);
		Document document = documents.findById(documentId)
			.orElseThrow(() -> EstimateRuleException.notFound("Document introuvable."));
		if (!document.getDossier().getTenderId().equals(estimate.getTenderId())) {
			throw EstimateRuleException.invalid("Ce document n'appartient pas au marché du chiffrage.");
		}
		List<EstimateItem> items = read(document);
		if (items.isEmpty()) {
			throw EstimateRuleException.invalid("Aucune ligne de prix reconnue dans « " + document.getFileName()
					+ " » : en-tête des colonnes (désignation, unité, quantité, prix) non trouvé.");
		}
		if (estimate.getItems().size() + items.size() > MAX_ITEMS) {
			throw EstimateRuleException.invalid("Un chiffrage compte au plus " + MAX_ITEMS + " postes.");
		}
		EstimateResponse response = estimates.change(estimateId, user, draft -> items.forEach(draft::add));
		long toCheck = items.stream().filter(EstimateItem::isToCheck).count();
		estimates.record(estimate, Kind.IMPORTED, items.size() + " poste(s) importé(s) de « " + document.getFileName()
				+ " »" + (toCheck > 0 ? ", dont " + toCheck + " à vérifier" : ""), user);
		log.info("{} items imported in estimate {} from document {} by {}", items.size(), estimateId, documentId, user);
		return response;
	}

	private List<EstimateItem> read(Document document) {
		List<EstimateItem> items = new ArrayList<>();
		try {
			if (SpreadsheetExtractor.handles(document.getFileName())) {
				for (Sheet sheet : spreadsheets.read(storage.resolve(document.getStoragePath()),
						DocumentProcessor.extension(document.getFileName())).sheets()) {
					PriceScheduleChecker.lines(sheet)
						.forEach(line -> items.add(item(line, document.getId(), sheet.name(), !line.complete())));
				}
			}
			else if (document.getPdfPath() != null) {
				// Pages read by OCR give their stored words, the others those of the PDF.
				List<PdfTable> tables = pdfTables.extract(storage.resolve(document.getPdfPath()),
						pages.findByDocumentIdOrderByPageNumber(document.getId()));
				for (int i = 0; i < tables.size(); i++) {
					String name = PdfTableText.name(tables.get(i), i + 1);
					PriceScheduleChecker.lines(PdfTableText.sheet(tables.get(i), i + 1))
						.forEach(line -> items.add(item(line, document.getId(), name, true)));
				}
			}
			else {
				throw EstimateRuleException.invalid("Ce document n'est ni un tableur ni un PDF lu : rien à importer.");
			}
		}
		catch (IOException ex) {
			// Its name and content are never logged: only which document and why.
			log.warn("Price schedule {} could not be read for an estimate: {}", document.getId(),
					ex.getClass().getSimpleName());
			throw EstimateRuleException.invalid("Le document n'a pas pu être relu : " + document.getFileName());
		}
		return items;
	}

	private static EstimateItem item(ScheduleLine line, Long documentId, String sheet, boolean toCheck) {
		BigDecimal quantity = line.quantity() == null ? null : line.quantity().setScale(3, RoundingMode.HALF_UP);
		if (quantity != null && quantity.precision() - quantity.scale() > 12) {
			quantity = null;
			toCheck = true;
		}
		ItemRequest request = new ItemRequest(cut(line.chapter(), 200), cut(line.reference(), 50),
				cut(line.designation(), 4000), cut(line.unit(), 20), quantity,
				line.textPrice() == null ? PriceMode.FIXED : PriceMode.TEXT, null, cut(line.textPrice(), 30), false,
				toCheck);
		return new EstimateItem(request, documentId, cut(sheet, 100), line.row());
	}

	private static String cut(String text, int length) {
		return text == null || text.length() <= length ? text : text.substring(0, length);
	}

}
