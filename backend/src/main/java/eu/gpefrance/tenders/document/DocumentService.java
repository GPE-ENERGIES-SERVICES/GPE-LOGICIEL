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
		return dossiers.findByTenderIdOrderByVersionDesc(tenderId).stream().map(DossierResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public DossierResponse dossier(Long dossierId) {
		return DossierResponse.from(findDossier(dossierId));
	}

	@Transactional
	public DossierResponse.DocumentResponse correctPieceType(Long documentId, PieceType pieceType) {
		Document document = find(documentId);
		document.correctPieceType(pieceType);
		return DossierResponse.DocumentResponse.from(document);
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
