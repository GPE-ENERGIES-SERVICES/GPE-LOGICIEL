package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Finds the real type of a retrieved file from its content, never from its
 * name nor the Content-Type of the server, among the formats of a
 * consultation file; anything else is refused. A page of a website (login,
 * withdrawal form) is refused whatever its name, as are an empty file, a
 * truncated PDF, a malformed archive, an archive beyond the limits of the
 * extraction (number of files, size, nested names leaving the folder) and a
 * name whose extension says another type than the content.
 */
public final class FileTypeValidator {

	/** Formats accepted, with the extensions each one may have. */
	public enum Kind {

		PDF("pdf"), ZIP("zip", "docx", "xlsx", "pptx", "odt", "ods", "odp"), OLE("doc", "xls", "ppt", "msg"),
		RTF("rtf"), SEVEN_ZIP("7z"), RAR("rar"), PNG("png"), JPEG("jpg", "jpeg"), TIFF("tif", "tiff"), DWG("dwg");

		private final Set<String> extensions;

		Kind(String... extensions) {
			this.extensions = Set.of(extensions);
		}

		public String defaultExtension() {
			return extensions.stream().sorted().findFirst().orElseThrow();
		}

		boolean accepts(String extension) {
			return extensions.contains(extension);
		}

	}

	private static final Map<Kind, byte[]> SIGNATURES = Map.of(Kind.PDF, bytes("%PDF-"),
			Kind.ZIP, new byte[] { 'P', 'K', 3, 4 },
			Kind.OLE, new byte[] { (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1 },
			Kind.RTF, bytes("{\\rtf"), Kind.SEVEN_ZIP, new byte[] { '7', 'z', (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C },
			Kind.RAR, bytes("Rar!\u001A\u0007"), Kind.PNG, new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n' },
			Kind.JPEG, new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }, Kind.DWG, bytes("AC10"));

	private static final byte[] TIFF_LITTLE = { 'I', 'I', 42, 0 };

	private static final byte[] TIFF_BIG = { 'M', 'M', 0, 42 };

	private static final int HEAD = 1024;

	private FileTypeValidator() {
	}

	/**
	 * @param fileName the name given by the platform, whose extension must
	 * agree with the content; a name without known extension takes the one of
	 * the content
	 * @return the type found and the name to keep
	 * @throws FileRejectedException when the file is not one of a consultation file
	 */
	public static Validated validate(Path file, String fileName, int maxArchiveEntries, long maxExtractedBytes)
			throws IOException {
		long size = Files.size(file);
		if (size == 0) {
			throw new FileRejectedException("Fichier vide");
		}
		byte[] head = head(file);
		if (looksLikeHtml(head)) {
			throw new FileRejectedException("Page web (connexion, formulaire de retrait ou erreur) au lieu d'un fichier");
		}
		Kind kind = detect(head);
		if (kind == null) {
			throw new FileRejectedException("Format non reconnu");
		}
		String name = FileStorage.safeName(fileName == null ? "" : fileName);
		String extension = DocumentProcessor.extension(name);
		if (extension.isEmpty() || !knownExtension(extension)) {
			name = (name.isBlank() ? "document" : name) + "." + kind.defaultExtension();
		}
		else if (!kind.accepts(extension)) {
			throw new FileRejectedException(
					"Le contenu (" + kind.name() + ") ne correspond pas à l'extension ." + extension);
		}
		if (kind == Kind.PDF) {
			checkPdfEnd(file, size);
		}
		if (kind == Kind.ZIP) {
			checkZip(file, maxArchiveEntries, maxExtractedBytes);
		}
		return new Validated(kind, name, size);
	}

	static Kind detect(byte[] head) {
		for (Map.Entry<Kind, byte[]> signature : SIGNATURES.entrySet()) {
			if (startsWith(head, signature.getValue())) {
				return signature.getKey();
			}
		}
		return startsWith(head, TIFF_LITTLE) || startsWith(head, TIFF_BIG) ? Kind.TIFF : null;
	}

	/** A web page, whatever the name or the type announced by the server. */
	static boolean looksLikeHtml(byte[] head) {
		String text = new String(head, StandardCharsets.ISO_8859_1).stripLeading().toLowerCase(Locale.ROOT);
		if (text.startsWith("﻿") || text.startsWith("ï»¿")) {
			text = text.substring(text.startsWith("﻿") ? 1 : 3).stripLeading();
		}
		return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<head")
				|| text.startsWith("<body") || text.startsWith("<?xml") && text.contains("<html")
				|| text.startsWith("<!--") && text.contains("<html");
	}

	private static boolean knownExtension(String extension) {
		return Arrays.stream(Kind.values()).anyMatch(kind -> kind.accepts(extension));
	}

	/** A complete PDF ends with %%EOF, possibly followed by a few bytes. */
	private static void checkPdfEnd(Path file, long size) throws IOException {
		int tail = (int) Math.min(HEAD, size);
		byte[] end = new byte[tail];
		try (RandomAccessFile in = new RandomAccessFile(file.toFile(), "r")) {
			in.seek(size - tail);
			in.readFully(end);
		}
		if (!new String(end, StandardCharsets.ISO_8859_1).contains("%%EOF")) {
			throw new FileRejectedException("PDF tronqué ou incomplet");
		}
	}

	/**
	 * Reads the directory of the archive without extracting it: the same
	 * limits as the extraction of an upload.
	 */
	private static void checkZip(Path file, int maxEntries, long maxBytes) throws IOException {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			int entries = 0;
			long total = 0;
			Enumeration<? extends ZipEntry> all = zip.entries();
			while (all.hasMoreElements()) {
				ZipEntry entry = all.nextElement();
				String entryName = entry.getName().replace('\\', '/');
				if (entryName.startsWith("/") || entryName.contains("../") || entryName.equals("..")
						|| entryName.matches("^[A-Za-z]:.*")) {
					throw new FileRejectedException("Archive contenant un chemin interdit : " + entry.getName());
				}
				if (++entries > maxEntries) {
					throw new FileRejectedException("Archive de plus de " + maxEntries + " fichiers");
				}
				if (entry.getSize() > 0) {
					total += entry.getSize();
				}
				if (total > maxBytes) {
					throw new FileRejectedException("Archive dont le contenu dépasse la taille maximale décompressée");
				}
			}
		}
		catch (ZipException ex) {
			throw new FileRejectedException("Archive illisible ou malformée");
		}
	}

	private static byte[] head(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return in.readNBytes(HEAD);
		}
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		return content.length >= prefix.length && Arrays.equals(content, 0, prefix.length, prefix, 0, prefix.length);
	}

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.ISO_8859_1);
	}

	/**
	 * @param fileName the name to keep, with the extension of its type
	 */
	public record Validated(Kind kind, String fileName, long size) {
	}

	public static class FileRejectedException extends RuntimeException {

		public FileRejectedException(String message) {
			super(message);
		}

	}

}
