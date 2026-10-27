package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document.OutputSettings;
import org.springframework.stereotype.Component;

/**
 * Extracts the text of plain text and HTML files, cut into sections that play
 * the role of pages for the citations. An HTML file is only parsed: scripts,
 * styles and forms are dropped, and nothing it refers to (images, styles,
 * links) is ever fetched. Files beyond {@link #MAX_BYTES} are not read.
 */
@Component
public class TextFileExtractor {

	/** Larger text files are not read: a consultation file is never that long in plain text. */
	static final long MAX_BYTES = 20L * 1024 * 1024;

	/** Length of a section, cut at a line break when possible. */
	static final int SECTION_LENGTH = 4000;

	private static final Charset WINDOWS = Charset.forName("windows-1252");

	/**
	 * @return the text of each section, in order
	 * @throws TooLargeException beyond {@link #MAX_BYTES}
	 */
	public List<String> extractText(Path file) throws IOException {
		return sections(read(file));
	}

	/**
	 * @return the visible text of each section, in order
	 * @throws TooLargeException beyond {@link #MAX_BYTES}
	 */
	public List<String> extractHtml(Path file) throws IOException {
		org.jsoup.nodes.Document html = Jsoup.parse(read(file));
		html.select("script, style, noscript, template, iframe, object, embed, form, svg").remove();
		html.outputSettings(new OutputSettings().prettyPrint(false));
		html.select("br").after("\n");
		html.select("p, div, li, tr, h1, h2, h3, h4, h5, h6, table, section, article").after("\n");
		return sections(html.body() == null ? "" : html.body().wholeText());
	}

	private static String read(Path file) throws IOException {
		if (Files.size(file) > MAX_BYTES) {
			throw new TooLargeException();
		}
		byte[] bytes = Files.readAllBytes(file);
		try {
			return StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes))
				.toString()
				.replace("﻿", "");
		}
		catch (CharacterCodingException ex) {
			// Files written on Windows are often not UTF-8.
			return new String(bytes, WINDOWS);
		}
	}

	static List<String> sections(String text) {
		String normalized = text.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[ \\t\\x0B]+\\n", "\n")
			.replaceAll("\\n{3,}", "\n\n")
			.strip();
		List<String> sections = new ArrayList<>();
		int start = 0;
		while (start < normalized.length()) {
			int end = Math.min(start + SECTION_LENGTH, normalized.length());
			if (end < normalized.length()) {
				int lineBreak = normalized.lastIndexOf('\n', end);
				if (lineBreak > start + SECTION_LENGTH / 2) {
					end = lineBreak;
				}
			}
			sections.add(normalized.substring(start, end).strip());
			start = end;
		}
		return sections;
	}

	public static class TooLargeException extends IOException {

		TooLargeException() {
			super("Text file too large");
		}

	}

}
