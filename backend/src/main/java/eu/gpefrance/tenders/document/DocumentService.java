package eu.gpefrance.tenders.document;

import java.nio.file.Path;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation of dossiers and documents.
 */
@Service
public class DocumentService {

	private final DossierRepository dossiers;

	private final DocumentRepository documents;

	private final DocumentPageRepository pages;

	private final FileStorage storage;

	public DocumentService(DossierRepository dossiers, DocumentRepository documents, DocumentPageRepository pages,
			FileStorage storage) {
		this.dossiers = dossiers;
		this.documents = documents;
		this.pages = pages;
		this.storage = storage;
	}

	@Transactional(readOnly = true)
	public List<DossierResponse> dossiersOf(Long tenderId) {
		return dossiers.findByTenderIdOrderByVersionDesc(tenderId).stream().map(this::response).toList();
	}

	@Transactional(readOnly = true)
	public DossierResponse dossier(Long dossierId) {
		return response(findDossier(dossierId));
	}

	private DossierResponse response(Dossier dossier) {
		return DossierResponse.from(dossier, DocumentTriage.decide(dossier.getDocuments(), pages));
	}

	private DossierResponse.DocumentResponse response(Document document) {
		return DossierResponse.DocumentResponse.from(document,
				DocumentTriage.decide(document.getDossier().getDocuments(), pages).get(document.getId()));
	}

	@Transactional
	public DossierResponse.DocumentResponse correctPieceType(Long documentId, PieceType pieceType) {
		Document document = find(documentId);
		document.correctPieceType(pieceType);
		return response(document);
	}

	/**
	 * Sends a document to the AI or keeps it out, whatever the rules, or lets
	 * the rules decide again (null). A document added whose scanned pages were
	 * not read is put back to be read, with OCR.
	 * @return the document, and whether its dossier must be read again
	 */
	@Transactional
	public AiChoiceResult chooseForAi(Long documentId, AiChoice choice) {
		Document document = find(documentId);
		document.chooseForAi(choice);
		// Scanned pages, or an image or a plan kept aside, are read once a user wants them.
		boolean reread = choice == AiChoice.INCLUDE
				&& (document.getState() == DocumentState.SCANNED && document.getPdfPath() != null
						|| document.getState() == DocumentState.NOT_ANALYZED
								&& DocumentProcessor.readable(document.getFileName()));
		if (reread) {
			document.resetForRetry();
		}
		return new AiChoiceResult(response(document), reread ? document.getDossier().getId() : null);
	}

	/**
	 * @param rereadDossier the dossier to read again, or null
	 */
	public record AiChoiceResult(DossierResponse.DocumentResponse document, Long rereadDossier) {
	}

	/**
	 * Lets the analysis of a dossier start by itself once read, or not.
	 */
	@Transactional
	public DossierResponse allowAutoAnalysis(Long dossierId, boolean allowed) {
		Dossier dossier = findDossier(dossierId);
		dossier.allowAutoAnalysis(allowed);
		return response(dossier);
	}

	/**
	 * Puts the documents that could not be read back in the queue.
	 * @return whether there was anything to read again
	 */
	@Transactional
	public boolean retryFailed(Long dossierId) {
		Dossier dossier = findDossier(dossierId);
		boolean retried = false;
		for (Document document : dossier.getDocuments()) {
			if (document.getState() == DocumentState.FAILED) {
				document.resetForRetry();
				retried = true;
			}
		}
		return retried;
	}

	@Transactional(readOnly = true)
	public StoredDocument original(Long documentId) {
		Document document = find(documentId);
		return new StoredDocument(document.getFileName(), storage.resolve(document.getStoragePath()));
	}

	/**
	 * The PDF to read: the document itself or its conversion.
	 */
	@Transactional(readOnly = true)
	public StoredDocument readablePdf(Long documentId) {
		Document document = find(documentId);
		if (document.getPdfPath() == null) {
			throw new DocumentNotFoundException(documentId);
		}
		return new StoredDocument(document.getFileName(), storage.resolve(document.getPdfPath()));
	}

	@Transactional(readOnly = true)
	public DocumentPage page(Long documentId, int pageNumber) {
		return pages.findByDocumentIdAndPageNumber(documentId, pageNumber)
			.orElseThrow(() -> new DocumentNotFoundException(documentId));
	}

	private Document find(Long documentId) {
		return documents.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
	}

	private Dossier findDossier(Long dossierId) {
		return dossiers.findById(dossierId).orElseThrow(() -> new DocumentNotFoundException(dossierId));
	}

	public record StoredDocument(String fileName, Path path) {
	}

}
