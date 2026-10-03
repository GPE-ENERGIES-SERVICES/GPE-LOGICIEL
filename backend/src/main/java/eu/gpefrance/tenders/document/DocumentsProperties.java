package eu.gpefrance.tenders.document;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * @param storageDir where uploaded files are kept, by tender and dossier version
 * @param officeCommand LibreOffice executable used to convert Word documents to PDF
 * @param conversionTimeout maximum duration of one conversion
 * @param maxArchiveEntries maximum number of files in an archive, nested ones included
 * @param maxExtractedSize maximum total size of the files extracted from one upload
 * @param maxArchiveDepth maximum nesting of archives inside archives
 */
@ConfigurationProperties("documents")
public record DocumentsProperties(String storageDir, String officeCommand, Duration conversionTimeout,
		int maxArchiveEntries, DataSize maxExtractedSize, int maxArchiveDepth) {
}
