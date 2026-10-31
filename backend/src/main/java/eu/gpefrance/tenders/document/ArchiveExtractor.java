package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.springframework.stereotype.Component;

/**
 * Extracts ZIP archives, including archives nested in archives, within
 * limits: number of files, total extracted size and nesting depth. Entry
 * names cannot leave the target directory.
 */
@Component
public class ArchiveExtractor {

	/** Encoding of file names in archives made by the Windows built-in tool. */
	private static final Charset WINDOWS_ZIP_CHARSET = Charset.forName("IBM850");

	private static final int BUFFER_SIZE = 64 * 1024;

	private final DocumentsProperties properties;

	public ArchiveExtractor(DocumentsProperties properties) {
		this.properties = properties;
	}

	public static boolean isZip(String fileName) {
		return fileName.toLowerCase(Locale.ROOT).endsWith(".zip");
	}

	/**
	 * @param displayName name shown to users for the archive, prefixed to the
	 * names of its files
	 * @return the extracted files, nested archives replaced by their content
	 */
	public List<ExtractedFile> extract(Path archive, String displayName, Path targetDirectory) {
		Budget budget = new Budget(properties.maxArchiveEntries(), properties.maxExtractedSize().toBytes());
		List<ExtractedFile> files = new ArrayList<>();
		extract(archive, displayName, targetDirectory, 1, budget, files);
		return files;
	}

	private void extract(Path archive, String displayName, Path targetDirectory, int depth, Budget budget,
			List<ExtractedFile> files) {
		if (depth > properties.maxArchiveDepth()) {
			throw new ArchiveRejectedException("Trop d'archives imbriquées dans " + displayName + " (maximum "
					+ properties.maxArchiveDepth() + ")");
		}
		try (ZipFile zip = open(archive)) {
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				List<String> segments = FileStorage.safeSegments(entry.getName());
				if (entry.isDirectory() || segments.isEmpty() || isSystemFile(segments)) {
					continue;
				}
				budget.countFile(displayName);
				Path target = targetDirectory;
				for (String segment : segments.subList(0, segments.size() - 1)) {
					target = target.resolve(segment);
				}
				Files.createDirectories(target);
				target = FileStorage.uniquePath(target, segments.getLast());
				if (!target.normalize().startsWith(targetDirectory.normalize())) {
					throw new ArchiveRejectedException("Chemin interdit dans l'archive " + displayName);
				}
				// The fingerprint is taken while writing: the file is never read again for it.
				MessageDigest sha256 = sha256();
				long size;
				try (InputStream in = zip.getInputStream(entry);
						OutputStream out = new DigestOutputStream(Files.newOutputStream(target), sha256)) {
					size = copy(in, out, budget, displayName);
				}
				String name = displayName + "/" + String.join("/", segments);
				if (isZip(target.getFileName().toString())) {
					Path nestedDirectory = target.resolveSibling(target.getFileName() + "_contenu");
					extract(target, name, nestedDirectory, depth + 1, budget, files);
					Files.delete(target);
				}
				else {
					files.add(new ExtractedFile(name, target, size, HexFormat.of().formatHex(sha256.digest())));
				}
			}
		}
		catch (IOException ex) {
			throw new ArchiveRejectedException("Archive illisible : " + displayName, ex);
		}
	}

	/**
	 * Archives made on Windows often encode names in IBM850 instead of UTF-8.
	 */
	private static ZipFile open(Path archive) throws IOException {
		try {
			ZipFile zip = new ZipFile(archive.toFile(), StandardCharsets.UTF_8);
			try {
				zip.stream().forEach(ZipEntry::getName);
				return zip;
			}
			catch (IllegalArgumentException ex) {
				zip.close();
			}
		}
		catch (ZipException ex) {
			// names that are not valid UTF-8 can already fail here
		}
		return new ZipFile(archive.toFile(), WINDOWS_ZIP_CHARSET);
	}

	private static boolean isSystemFile(List<String> segments) {
		return segments.contains("__MACOSX") || segments.getLast().equalsIgnoreCase("Thumbs.db")
				|| segments.getLast().equalsIgnoreCase("desktop.ini");
	}

	/**
	 * @return the bytes copied
	 */
	private static long copy(InputStream in, OutputStream out, Budget budget, String displayName)
			throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		long size = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			budget.countBytes(read, displayName);
			out.write(buffer, 0, read);
			size += read;
		}
		return size;
	}

	static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @param sha256 the fingerprint of its content, in hexadecimal
	 */
	public record ExtractedFile(String displayName, Path path, long size, String sha256) {
	}

	/**
	 * What remains allowed for the whole upload, nested archives included:
	 * protects against archives that expand to huge sizes.
	 */
	private static final class Budget {

		private int filesLeft;

		private long bytesLeft;

		private final long maxBytes;

		Budget(int maxFiles, long maxBytes) {
			this.filesLeft = maxFiles;
			this.bytesLeft = maxBytes;
			this.maxBytes = maxBytes;
		}

		void countFile(String archive) {
			if (--filesLeft < 0) {
				throw new ArchiveRejectedException("Trop de fichiers dans l'archive " + archive);
			}
		}

		void countBytes(int bytes, String archive) {
			bytesLeft -= bytes;
			if (bytesLeft < 0) {
				throw new ArchiveRejectedException("L'archive " + archive
						+ " dépasse la taille maximale décompressée (" + maxBytes / (1024 * 1024) + " Mo)");
			}
		}

	}

	/**
	 * An archive refused: unreadable, too large or suspicious.
	 */
	public static class ArchiveRejectedException extends RuntimeException {

		public ArchiveRejectedException(String message) {
			super(message);
		}

		public ArchiveRejectedException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
