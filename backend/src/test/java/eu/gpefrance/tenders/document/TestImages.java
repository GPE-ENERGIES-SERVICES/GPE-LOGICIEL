package eu.gpefrance.tenders.document;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Images made for the tests, like the photos and scans of a consultation
 * file: pages of a test PDF drawn as images, turned, degraded, saved in the
 * formats met. No real document.
 */
public final class TestImages {

	private TestImages() {
	}

	/** The first page of a PDF drawn as an image, in grey levels. */
	public static BufferedImage page(byte[] pdf, float dpi) {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFRenderer(document).renderImageWithDPI(0, dpi, ImageType.GRAY);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A plain image of the given size, light grey with a dark square in its top left corner. */
	public static BufferedImage marked(int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(Color.LIGHT_GRAY);
		graphics.fillRect(0, 0, width, height);
		graphics.setColor(Color.BLACK);
		graphics.fillRect(0, 0, width / 4, height / 4);
		graphics.dispose();
		return image;
	}

	/**
	 * A poor photocopy: reduced to the given resolution, then enlarged back,
	 * with grey noise.
	 */
	public static BufferedImage degraded(BufferedImage image, double scale) {
		int width = (int) (image.getWidth() * scale);
		int height = (int) (image.getHeight() * scale);
		BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D down = small.createGraphics();
		down.drawImage(image, 0, 0, width, height, null);
		down.dispose();
		BufferedImage back = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		Graphics2D up = back.createGraphics();
		up.drawImage(small, 0, 0, image.getWidth(), image.getHeight(), null);
		up.dispose();
		Random random = new Random(7);
		for (int y = 0; y < back.getHeight(); y++) {
			for (int x = 0; x < back.getWidth(); x++) {
				int grey = back.getRaster().getSample(x, y, 0) + (int) (random.nextGaussian() * 40);
				back.getRaster().setSample(x, y, 0, Math.clamp(grey, 0, 255));
			}
		}
		return back;
	}

	public static byte[] write(BufferedImage image, String format) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			if (!ImageIO.write(image, format, out)) {
				throw new IllegalArgumentException("No writer for " + format);
			}
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A TIFF holding several images, like a scanner gives several pages. */
	public static byte[] tiff(List<BufferedImage> images) {
		ImageWriter writer = ImageIO.getImageWritersByFormatName("tiff").next();
		try (ByteArrayOutputStream out = new ByteArrayOutputStream();
				ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			writer.prepareWriteSequence(null);
			for (BufferedImage image : images) {
				writer.writeToSequence(new IIOImage(image, null, null), param);
			}
			writer.endWriteSequence();
			stream.flush();
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		finally {
			writer.dispose();
		}
	}

	/**
	 * A JPEG with the orientation a camera saves in its EXIF data (6: to be
	 * turned a quarter clockwise), the pixels left as taken.
	 */
	public static byte[] jpegWithOrientation(BufferedImage image, int orientation) {
		byte[] jpeg = write(image, "jpg");
		byte[] tiff = { 'M', 'M', 0, 42, 0, 0, 0, 8, // big endian, first directory at 8
				0, 1, // one entry
				0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, // Orientation, SHORT, 1 value
				0, 0, 0, 0 }; // no next directory
		byte[] exif = "Exif\0\0".getBytes(StandardCharsets.ISO_8859_1);
		int length = 2 + exif.length + tiff.length;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			out.write(jpeg, 0, 2);
			out.write(new byte[] { (byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length });
			out.write(exif);
			out.write(tiff);
			out.write(jpeg, 2, jpeg.length - 2);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
