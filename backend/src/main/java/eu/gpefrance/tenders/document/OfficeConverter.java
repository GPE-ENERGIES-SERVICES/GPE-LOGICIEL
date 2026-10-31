package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Converts Word documents to PDF with LibreOffice in headless mode. Macros
 * are never run: LibreOffice does not execute them during a conversion.
 */
@Component
public class OfficeConverter {

	private final DocumentsProperties properties;

	/**
	 * Profiles of LibreOffice not in use: one is used by one conversion at a
	 * time, then kept for the next one, which then starts several seconds
	 * sooner than with a new profile.
	 */
	private final Queue<Path> idleProfiles = new ConcurrentLinkedQueue<>();

	public OfficeConverter(DocumentsProperties properties) {
		this.properties = properties;
	}

	/**
	 * @return the PDF created in the output directory
	 * @throws ConversionException if LibreOffice is missing, fails or takes too long
	 */
	public Path toPdf(Path source, Path outputDirectory) {
		Path profile = null;
		boolean reusable = false;
		try {
			Files.createDirectories(outputDirectory);
			// A profile per conversion lets several conversions run at the same time.
			profile = idleProfiles.poll();
			if (profile == null) {
				profile = Files.createTempDirectory("office-profile");
			}
			Process process = new ProcessBuilder(List.of(properties.officeCommand(), "--headless", "--norestore",
					"-env:UserInstallation=" + profile.toUri(), "--convert-to", "pdf", "--outdir",
					outputDirectory.toString(), source.toString()))
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
			if (!process.waitFor(properties.conversionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new ConversionException("Conversion en PDF trop longue, abandonnée");
			}
			Path pdf = outputDirectory.resolve(baseName(source) + ".pdf");
			if (process.exitValue() != 0 || !Files.exists(pdf)) {
				throw new ConversionException("La conversion en PDF a échoué");
			}
			reusable = true;
			return pdf;
		}
		catch (IOException ex) {
			throw new ConversionException("Conversion en PDF indisponible (LibreOffice introuvable)", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ConversionException("Conversion en PDF interrompue", ex);
		}
		finally {
			// After a failure, the profile may be left damaged or locked: never used again.
			if (reusable) {
				idleProfiles.offer(profile);
			}
			else if (profile != null) {
				deleteQuietly(profile);
			}
		}
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	/**
	 * Prepares a profile of LibreOffice in the background once the
	 * application is ready: the first Word document read then does not wait
	 * for it.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void warmUp() {
		Thread.ofVirtual().name("office-warm-up").start(() -> {
			Path folder = null;
			try {
				folder = Files.createTempDirectory("office-warm-up");
				toPdf(Files.writeString(folder.resolve("essai.txt"), "GPE"), folder);
			}
			catch (IOException | ConversionException ex) {
				// LibreOffice missing: said at the first real conversion
			}
			finally {
				if (folder != null) {
					deleteQuietly(folder);
				}
			}
		});
	}

	@PreDestroy
	void deleteProfiles() {
		for (Path profile = idleProfiles.poll(); profile != null; profile = idleProfiles.poll()) {
			deleteQuietly(profile);
		}
	}

	private static void deleteQuietly(Path directory) {
		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
		catch (IOException ex) {
			// a temporary profile left behind is harmless
		}
	}

	public static class ConversionException extends RuntimeException {

		public ConversionException(String message) {
			super(message);
		}

		public ConversionException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
