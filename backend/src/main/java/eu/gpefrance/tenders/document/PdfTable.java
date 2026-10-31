package eu.gpefrance.tenders.document;

import java.util.List;

/**
 * A table rebuilt from the positions of the words of a digital PDF, on one
 * page or continued over several. Positions are in points from the top left
 * of the page.
 * @param pages the pages it covers, in order
 * @param columns the columns, from the header
 * @param headerLines how many lines the header takes
 * @param rows the lines under the header, in reading order
 * @param reliable whether every value fell clearly in one column; when false,
 * {@link #problems} say where the layout is ambiguous
 * @param problems why the structure is ambiguous, first ones only
 */
public record PdfTable(List<Integer> pages, List<Column> columns, int headerLines, List<Row> rows, boolean reliable,
		List<String> problems) {

	/**
	 * @param label the header, its lines joined
	 * @param left left edge of the column, where the previous one ends
	 * @param right right edge, where the next one starts
	 */
	public record Column(String label, float left, float right) {
	}

	/**
	 * @param page the page of the line
	 * @param y position of its baseline
	 * @param cells one per column holding something
	 */
	public record Row(int page, float y, List<Cell> cells) {
	}

	/**
	 * @param column index of its column
	 * @param x0 left edge of its text
	 * @param x1 right edge of its text
	 * @param ambiguous whether its text crosses the edge of the column, or
	 * holds values far apart that may belong to two columns
	 */
	public record Cell(int column, String text, float x0, float x1, boolean ambiguous) {
	}

}
