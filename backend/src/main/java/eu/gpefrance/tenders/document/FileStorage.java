package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * The directory where uploaded files are kept. Paths stored in the database
 * are relative to it, so the directory can be moved.
 */
@Component
public class FileStorage {

	private static final int MAX_NAME_LENGTH = 150;

	private final Path root;

	public FileStorage(DocumentsProperties properties) {
		this.root = Path.of(properties.storageDir()).toAbsolutePath().normalize();
	}

	/**
	 * Resolves a stored path, refusing anything outside the storage directory.
	 */
	public Path resolve(String relativePath) {
		Path path = root.resolve(relativePath).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Path outside the storage directory: " + relativePath);
		}
		return path;
	}

	public String relativize(Path path) {
		return root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
	}

	/**
	 * Copies the content to a new file in the given directory, under a safe
	 * version of the name; a number is added if the name is already taken.
	 */
	public Path store(InputStream content, Path directory, String fileName) {
		try {
			Files.createDirectories(directory);
			Path target = uniquePath(directory, safeName(fileName));
			Files.copy(content, target);
			return target;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static Path uniquePath(Path directory, String name) {
		Path candidate = directory.resolve(name);
		int dot = name.lastIndexOf('.');
		String base = dot > 0 ? name.substring(0, dot) : name;
		String extension = dot > 0 ? name.substring(dot) : "";
		for (int i = 2; Files.exists(candidate); i++) {
			candidate = directory.resolve(base + " (" + i + ")" + extension);
		}
		return candidate;
	}

	/**
	 * A file name without any directory part or character that could escape
	 * the target directory or break on the file system.
	 */
	static String safeName(String name) {
		String last = name == null ? "" : name.replace('\\', '/');
		last = last.substring(last.lastIndexOf('/') + 1);
		String cleaned = last.replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
		while (cleaned.startsWith(".")) {
			cleaned = cleaned.substring(1);
		}
		if (cleaned.isBlank()) {
			cleaned = "fichier";
		}
		return cleaned.length() <= MAX_NAME_LENGTH ? cleaned : cleaned.substring(cleaned.length() - MAX_NAME_LENGTH);
	}

	/**
	 * The directory parts of an archive entry, without anything that could
	 * leave the extraction directory ("..", absolute paths, drive letters).
	 */
	static List<String> safeSegments(String entryName) {
		List<String> segments = new ArrayList<>();
		for (String segment : entryName.replace('\\', '/').split("/")) {
			if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.endsWith(":")) {
				continue;
			}
			segments.add(safeName(segment));
		}
		return segments;
	}

}
