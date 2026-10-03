package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Converts Word documents to PDF with LibreOffice in headless mode. Macros
 * are never run: LibreOffice does not execute them during a conversion.
 */
@Component
public class OfficeConverter {

	private final DocumentsProperties properties;

	public OfficeConverter(DocumentsProperties properties) {
		this.properties = properties;
	}

	/**
	 * @return the PDF created in the output directory
	 * @throws ConversionException if LibreOffice is missing, fails or takes too long
	 */
	public Path toPdf(Path source, Path outputDirectory) {
		Path profile = null;
		try {
			Files.createDirectories(outputDirectory);
			// A profile per conversion lets several conversions run at the same time.
			profile = Files.createTempDirectory("office-profile");
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
			if (profile != null) {
				deleteQuietly(profile);
			}
		}
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
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
