package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Extracts the text of a PDF, one entry per page.
 */
@Component
public class PdfTextExtractor {

	/**
	 * @return the text of each page, in order; a page without text (scanned)
	 * gives an empty string
	 */
	public List<String> extractPages(Path pdf) throws IOException {
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			List<String> pages = new ArrayList<>(document.getNumberOfPages());
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				pages.add(stripper.getText(document).strip());
			}
			return pages;
		}
	}

}
