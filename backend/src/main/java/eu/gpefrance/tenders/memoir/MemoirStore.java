package eu.gpefrance.tenders.memoir;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The tables of the memoirs, read and written with plain SQL. The outline is
 * a tree: it is read whole and given in reading order, each section with its
 * level and its number (« 2.1.3 »).
 */
@Component
class MemoirStore {

	private final JdbcTemplate jdbc;

	MemoirStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/**
	 * @param sections sections of the outline, proposed ones excluded
	 * @param written of them, those with a content
	 */
	public record MemoirRow(Long id, Long tenderId, String tenderTitle, String lotNumber, String title,
			MemoirStatus status, String outlineValidatedBy, Instant outlineValidatedAt, String createdBy,
			Instant createdAt, String updatedBy, Instant updatedAt, int sections, int written) {
	}

	/**
	 * @param level 1 for a part, 2 for a sub-part...
	 * @param number its place in the outline, « 2.1 »
	 * @param proposed suggested by the AI, waiting to be accepted
	 * @param contentOrigin MANUAL, AI or COMPANY
	 * @param proposal a content waiting for the user before it replaces the content
	 */
	public record SectionRow(Long id, Long memoirId, Long parentId, int position, int level, String number,
			String title, String description, boolean proposed, String content, String contentOrigin, String proposal,
			String proposalNote, List<Long> requirementIds, String updatedBy, Instant updatedAt) {
	}

	/**
	 * @param category CRITERION, SUBCRITERION, MANDATORY, FORM, MEANS, DEADLINE, METHOD, OTHER
	 * @param verified the quote was found on the page cited
	 * @param origin AI or USER
	 */
	public record RequirementRow(Long id, int position, String category, String text, String weight, Long documentId,
			Integer page, String quote, boolean verified, String origin) {
	}

	public record JobRow(Long id, String kind, String status, Long currentSectionId, int doneSteps, int totalSteps,
			boolean cancelRequested, String message, String startedBy, Instant startedAt, Instant finishedAt) {
	}

	public record RevisionRow(Long id, String content, String origin, String replacedBy, Instant createdAt) {
	}

	public record FileRow(Long id, Long memoirId, String fileName, String storagePath) {
	}

	/**
	 * @param textChars length of the text read in the file, 0 when none could be read
	 */
	public record LibraryRow(Long id, String title, LocalDate writtenOn, String context, String marketType,
			String activityCode, boolean useForAi, String fileName, long sizeBytes, int textChars, String uploadedBy,
			Instant uploadedAt) {
	}

	// Memoirs

	private static final String MEMOIR = """
			SELECT m.*, t.title AS tender_title,
			    (SELECT count(*) FROM memoir_section s WHERE s.memoir_id = m.id AND NOT s.proposed) AS sections,
			    (SELECT count(*) FROM memoir_section s WHERE s.memoir_id = m.id AND NOT s.proposed
			        AND s.content IS NOT NULL) AS written
			FROM memoir m LEFT JOIN tender t ON t.id = m.tender_id
			""";

	Long insertMemoir(Long tenderId, String lotNumber, String title, String user, Instant now) {
		return jdbc.queryForObject("""
				INSERT INTO memoir (tender_id, lot_number, title, status, created_by, created_at, updated_by, updated_at)
				VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?) RETURNING id""", Long.class, tenderId, lotNumber, title, user,
				ts(now), user, ts(now));
	}

	List<MemoirRow> memoirs() {
		return jdbc.query(MEMOIR + " ORDER BY m.updated_at DESC, m.id DESC", MemoirStore::mapMemoir);
	}

	Optional<MemoirRow> memoir(Long id) {
		return jdbc.query(MEMOIR + " WHERE m.id = ?", MemoirStore::mapMemoir, id).stream().findFirst();
	}

	void updateMemoir(Long id, String title, String lotNumber, MemoirStatus status, String user, Instant now) {
		jdbc.update("UPDATE memoir SET title = ?, lot_number = ?, status = ?, updated_by = ?, updated_at = ? WHERE id = ?",
				title, lotNumber, status.name(), user, ts(now), id);
	}

	void setStatus(Long id, MemoirStatus status) {
		jdbc.update("UPDATE memoir SET status = ? WHERE id = ?", status.name(), id);
	}

	void touch(Long id, String user, Instant now) {
		jdbc.update("UPDATE memoir SET updated_by = ?, updated_at = ? WHERE id = ?", user, ts(now), id);
	}

	void validateOutline(Long id, String user, Instant now) {
		jdbc.update("""
				UPDATE memoir SET outline_validated_by = ?, outline_validated_at = ?, status = 'READY_TO_WRITE',
				    updated_by = ?, updated_at = ? WHERE id = ?""", user, ts(now), user, ts(now), id);
	}

	/** An outline changed after its validation is to be validated again. */
	void outlineChanged(Long id) {
		jdbc.update("""
				UPDATE memoir SET outline_validated_by = NULL, outline_validated_at = NULL,
				    status = CASE WHEN status IN ('DRAFT', 'READY_TO_WRITE') THEN 'OUTLINE_TO_VALIDATE' ELSE status END
				WHERE id = ?""", id);
	}

	void deleteMemoir(Long id) {
		jdbc.update("DELETE FROM memoir WHERE id = ?", id);
	}

	// Sections

	List<SectionRow> sections(Long memoirId) {
		Map<Long, List<Long>> links = new HashMap<>();
		jdbc.query("""
				SELECT l.section_id, l.requirement_id FROM memoir_section_requirement l
				JOIN memoir_section s ON s.id = l.section_id WHERE s.memoir_id = ? ORDER BY l.requirement_id""",
				row -> {
					links.computeIfAbsent(row.getLong(1), id -> new ArrayList<>()).add(row.getLong(2));
				}, memoirId);
		List<SectionRow> all = jdbc.query("SELECT * FROM memoir_section WHERE memoir_id = ?",
				(row, n) -> mapSection(row, links), memoirId);
		return ordered(all);
	}

	Optional<SectionRow> section(Long id) {
		return jdbc.query("SELECT memoir_id FROM memoir_section WHERE id = ?", (row, n) -> row.getLong(1), id)
			.stream()
			.findFirst()
			.flatMap(memoirId -> sections(memoirId).stream().filter(section -> section.id().equals(id)).findFirst());
	}

	Long insertSection(Long memoirId, Long parentId, String title, String description, boolean proposed, String user,
			Instant now) {
		Integer last = jdbc.queryForObject("""
				SELECT max(position) FROM memoir_section WHERE memoir_id = ? AND parent_id IS NOT DISTINCT FROM ?""",
				Integer.class, memoirId, parentId);
		return jdbc.queryForObject("""
				INSERT INTO memoir_section (memoir_id, parent_id, position, title, description, proposed, updated_by,
				    updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, memoirId, parentId,
				last == null ? 1 : last + 1, title, description, proposed, user, ts(now));
	}

	void updateSection(Long id, String title, String description, String user, Instant now) {
		jdbc.update("UPDATE memoir_section SET title = ?, description = ?, updated_by = ?, updated_at = ? WHERE id = ?",
				title, description, user, ts(now), id);
	}

	void acceptSection(Long id) {
		jdbc.update("UPDATE memoir_section SET proposed = FALSE WHERE id = ?", id);
	}

	void deleteSection(Long id) {
		jdbc.update("DELETE FROM memoir_section WHERE id = ?", id);
	}

	void place(Long id, Long parentId, int position) {
		jdbc.update("UPDATE memoir_section SET parent_id = ?, position = ? WHERE id = ?", parentId, position, id);
	}

	/** Replaces the content; the former one, if any, is kept as a revision. */
	void setContent(Long id, String content, String origin, String user, Instant now) {
		jdbc.update("""
				INSERT INTO memoir_section_revision (section_id, content, origin, replaced_by, created_at)
				SELECT id, content, content_origin, ?, ? FROM memoir_section WHERE id = ? AND content IS NOT NULL
				    AND content IS DISTINCT FROM ?""", user, ts(now), id, content);
		jdbc.update("""
				UPDATE memoir_section SET content = ?, content_origin = ?, updated_by = ?, updated_at = ? WHERE id = ?""",
				content, content == null ? null : origin, user, ts(now), id);
	}

	/** Saves a manual content; a revision only when it replaces what someone or something else wrote. */
	void saveManual(Long id, String content, String user, Instant now) {
		jdbc.update("""
				INSERT INTO memoir_section_revision (section_id, content, origin, replaced_by, created_at)
				SELECT id, content, content_origin, ?, ? FROM memoir_section WHERE id = ? AND content IS NOT NULL
				    AND content_origin IS DISTINCT FROM 'MANUAL' AND content IS DISTINCT FROM ?""", user, ts(now), id,
				content);
		jdbc.update("""
				UPDATE memoir_section SET content = ?, content_origin = ?, updated_by = ?, updated_at = ? WHERE id = ?""",
				content, content == null ? null : "MANUAL", user, ts(now), id);
	}

	/**
	 * Writes the content only when the section has none, in one statement:
	 * a content saved meanwhile by a user is never written over.
	 * @return false when the section already had a content
	 */
	boolean fillIfEmpty(Long id, String content, String origin, String user, Instant now) {
		return jdbc.update("""
				UPDATE memoir_section SET content = ?, content_origin = ?, updated_by = ?, updated_at = ?
				WHERE id = ? AND content IS NULL""", content, origin, user, ts(now), id) == 1;
	}

	void setProposal(Long id, String proposal, String note, String user, Instant now) {
		jdbc.update("""
				UPDATE memoir_section SET proposal = ?, proposal_note = ?, updated_by = ?, updated_at = ? WHERE id = ?""",
				proposal, note, user, ts(now), id);
	}

	List<RevisionRow> revisions(Long sectionId) {
		return jdbc.query("SELECT * FROM memoir_section_revision WHERE section_id = ? ORDER BY id DESC",
				(row, n) -> new RevisionRow(row.getLong("id"), row.getString("content"), row.getString("origin"),
						row.getString("replaced_by"), row.getTimestamp("created_at").toInstant()),
				sectionId);
	}

	Optional<RevisionRow> revision(Long sectionId, Long revisionId) {
		return revisions(sectionId).stream().filter(revision -> revision.id().equals(revisionId)).findFirst();
	}

	void linkRequirements(Long sectionId, List<Long> requirementIds) {
		jdbc.update("DELETE FROM memoir_section_requirement WHERE section_id = ?", sectionId);
		for (Long requirementId : requirementIds) {
			jdbc.update("INSERT INTO memoir_section_requirement (section_id, requirement_id) VALUES (?, ?)", sectionId,
					requirementId);
		}
	}

	// Requirements

	List<RequirementRow> requirements(Long memoirId) {
		return jdbc.query("SELECT * FROM memoir_requirement WHERE memoir_id = ? ORDER BY position, id",
				(row, n) -> new RequirementRow(row.getLong("id"), row.getInt("position"), row.getString("category"),
						row.getString("text"), row.getString("weight"), (Long) row.getObject("document_id"),
						(Integer) row.getObject("page"), row.getString("quote"), row.getBoolean("verified"),
						row.getString("origin")),
				memoirId);
	}

	Long insertRequirement(Long memoirId, String category, String text, String weight, Long documentId, Integer page,
			String quote, boolean verified, String origin) {
		Integer last = jdbc.queryForObject("SELECT max(position) FROM memoir_requirement WHERE memoir_id = ?",
				Integer.class, memoirId);
		return jdbc.queryForObject("""
				INSERT INTO memoir_requirement (memoir_id, position, category, text, weight, document_id, page, quote,
				    verified, origin)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, memoirId,
				last == null ? 1 : last + 1, category, text, weight, documentId, page, quote, verified, origin);
	}

	void deleteRequirement(Long memoirId, Long id) {
		if (jdbc.update("DELETE FROM memoir_requirement WHERE id = ? AND memoir_id = ?", id, memoirId) == 0) {
			throw MemoirRuleException.notFound("Exigence introuvable.");
		}
	}

	/** The requirements read by the AI are replaced by a new analysis; those entered by a user stay. */
	void deleteAiRequirements(Long memoirId) {
		jdbc.update("DELETE FROM memoir_requirement WHERE memoir_id = ? AND origin = 'AI'", memoirId);
	}

	void deleteProposedSections(Long memoirId) {
		jdbc.update("DELETE FROM memoir_section WHERE memoir_id = ? AND proposed", memoirId);
	}

	// Files

	Long insertFile(Long memoirId, String fileName, String storagePath, long size, String user, Instant now) {
		return jdbc.queryForObject("""
				INSERT INTO memoir_file (memoir_id, file_name, storage_path, size_bytes, uploaded_by, uploaded_at)
				VALUES (?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, memoirId, fileName, storagePath, size, user,
				ts(now));
	}

	Optional<FileRow> file(Long id) {
		return jdbc.query("SELECT * FROM memoir_file WHERE id = ?", (row, n) -> new FileRow(row.getLong("id"),
				row.getLong("memoir_id"), row.getString("file_name"), row.getString("storage_path")), id)
			.stream()
			.findFirst();
	}

	List<String> filePaths(Long memoirId) {
		return jdbc.queryForList("SELECT storage_path FROM memoir_file WHERE memoir_id = ?", String.class, memoirId);
	}

	// Jobs

	/** @throws MemoirRuleException when a work of the AI is already running on the memoir */
	Long startJob(Long memoirId, String kind, int totalSteps, String user, Instant now) {
		try {
			return jdbc.queryForObject("""
					INSERT INTO memoir_job (memoir_id, kind, status, total_steps, started_by, started_at)
					VALUES (?, ?, 'RUNNING', ?, ?, ?) RETURNING id""", Long.class, memoirId, kind, totalSteps, user,
					ts(now));
		}
		catch (DuplicateKeyException ex) {
			throw MemoirRuleException.conflict("L'IA travaille déjà sur ce mémoire : attendez la fin ou arrêtez-la.");
		}
	}

	Optional<JobRow> lastJob(Long memoirId) {
		return jdbc.query("SELECT * FROM memoir_job WHERE memoir_id = ? ORDER BY id DESC LIMIT 1", MemoirStore::mapJob,
				memoirId).stream().findFirst();
	}

	void progress(Long jobId, Long sectionId, int doneSteps) {
		jdbc.update("UPDATE memoir_job SET current_section_id = ?, done_steps = ? WHERE id = ?", sectionId, doneSteps,
				jobId);
	}

	boolean cancelRequested(Long jobId) {
		return Boolean.TRUE.equals(
				jdbc.queryForObject("SELECT cancel_requested FROM memoir_job WHERE id = ?", Boolean.class, jobId));
	}

	boolean requestCancel(Long memoirId) {
		return jdbc.update("UPDATE memoir_job SET cancel_requested = TRUE WHERE memoir_id = ? AND status = 'RUNNING'",
				memoirId) > 0;
	}

	void finishJob(Long jobId, String status, String message, Instant now) {
		jdbc.update("""
				UPDATE memoir_job SET status = ?, message = ?, current_section_id = NULL, finished_at = ? WHERE id = ?""",
				status, message, ts(now), jobId);
	}

	/** Works left running by a stop of the application. */
	int interruptRunningJobs(Instant now) {
		return jdbc.update("""
				UPDATE memoir_job SET status = 'FAILED', message = 'Interrompu par un redémarrage du serveur.',
				    finished_at = ? WHERE status = 'RUNNING'""", ts(now));
	}

	// Library

	private static final String LIBRARY = """
			SELECT id, title, written_on, context, market_type, activity_code, use_for_ai, file_name, size_bytes,
			    coalesce(length(text), 0) AS text_chars, uploaded_by, uploaded_at FROM memoir_library
			""";

	List<LibraryRow> library() {
		return jdbc.query(LIBRARY + " ORDER BY written_on DESC NULLS LAST, id DESC", MemoirStore::mapLibrary);
	}

	Optional<LibraryRow> libraryEntry(Long id) {
		return jdbc.query(LIBRARY + " WHERE id = ?", MemoirStore::mapLibrary, id).stream().findFirst();
	}

	Long insertLibrary(String title, LocalDate writtenOn, String context, String marketType, String activityCode,
			boolean useForAi, String fileName, String storagePath, long size, String text, String user, Instant now) {
		return jdbc.queryForObject("""
				INSERT INTO memoir_library (title, written_on, context, market_type, activity_code, use_for_ai,
				    file_name, storage_path, size_bytes, text, uploaded_by, uploaded_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id""", Long.class, title, writtenOn, context,
				marketType, activityCode, useForAi, fileName, storagePath, size, text, user, ts(now));
	}

	void updateLibrary(Long id, String title, LocalDate writtenOn, String context, String marketType,
			String activityCode, boolean useForAi) {
		jdbc.update("""
				UPDATE memoir_library SET title = ?, written_on = ?, context = ?, market_type = ?, activity_code = ?,
				    use_for_ai = ? WHERE id = ?""", title, writtenOn, context, marketType, activityCode, useForAi, id);
	}

	Optional<String> libraryPath(Long id) {
		return jdbc.queryForList("SELECT storage_path FROM memoir_library WHERE id = ?", String.class, id)
			.stream()
			.findFirst();
	}

	void deleteLibrary(Long id) {
		jdbc.update("DELETE FROM memoir_library WHERE id = ?", id);
	}

	/**
	 * Excerpts of the former memoirs chosen for the AI, the most recent first.
	 */
	List<String> libraryExcerpts(int count, int length) {
		return jdbc.queryForList("""
				SELECT title || coalesce(' (' || context || ')', '') || E'\\n' || left(text, ?) FROM memoir_library
				WHERE use_for_ai AND text IS NOT NULL ORDER BY written_on DESC NULLS LAST, id DESC LIMIT ?""",
				String.class, length, count);
	}

	// Mapping

	/** Reading order: each part followed by its sub-parts, numbered. */
	private static List<SectionRow> ordered(List<SectionRow> all) {
		Map<Long, List<SectionRow>> children = new HashMap<>();
		for (SectionRow section : all) {
			children.computeIfAbsent(section.parentId(), id -> new ArrayList<>()).add(section);
		}
		children.values().forEach(list -> list.sort(Comparator.comparingInt(SectionRow::position)
			.thenComparing(SectionRow::id)));
		List<SectionRow> ordered = new ArrayList<>();
		walk(children, null, 1, "", ordered);
		return List.copyOf(ordered);
	}

	private static void walk(Map<Long, List<SectionRow>> children, Long parentId, int level, String prefix,
			List<SectionRow> ordered) {
		int index = 0;
		for (SectionRow section : children.getOrDefault(parentId, List.of())) {
			String number = section.proposed() ? "" : prefix + (++index);
			ordered.add(new SectionRow(section.id(), section.memoirId(), section.parentId(), section.position(), level,
					number, section.title(), section.description(), section.proposed(), section.content(),
					section.contentOrigin(), section.proposal(), section.proposalNote(), section.requirementIds(),
					section.updatedBy(), section.updatedAt()));
			walk(children, section.id(), level + 1, number.isEmpty() ? prefix : number + ".", ordered);
		}
	}

	private static MemoirRow mapMemoir(ResultSet row, int n) throws SQLException {
		Timestamp validated = row.getTimestamp("outline_validated_at");
		return new MemoirRow(row.getLong("id"), (Long) row.getObject("tender_id"), row.getString("tender_title"),
				row.getString("lot_number"), row.getString("title"), MemoirStatus.valueOf(row.getString("status")),
				row.getString("outline_validated_by"), validated == null ? null : validated.toInstant(),
				row.getString("created_by"), row.getTimestamp("created_at").toInstant(), row.getString("updated_by"),
				row.getTimestamp("updated_at").toInstant(), row.getInt("sections"), row.getInt("written"));
	}

	private static SectionRow mapSection(ResultSet row, Map<Long, List<Long>> links) throws SQLException {
		Long id = row.getLong("id");
		return new SectionRow(id, row.getLong("memoir_id"), (Long) row.getObject("parent_id"), row.getInt("position"),
				0, "", row.getString("title"), row.getString("description"), row.getBoolean("proposed"),
				row.getString("content"), row.getString("content_origin"), row.getString("proposal"),
				row.getString("proposal_note"), links.getOrDefault(id, List.of()), row.getString("updated_by"),
				row.getTimestamp("updated_at").toInstant());
	}

	private static JobRow mapJob(ResultSet row, int n) throws SQLException {
		Timestamp finished = row.getTimestamp("finished_at");
		return new JobRow(row.getLong("id"), row.getString("kind"), row.getString("status"),
				(Long) row.getObject("current_section_id"), row.getInt("done_steps"), row.getInt("total_steps"),
				row.getBoolean("cancel_requested"), row.getString("message"), row.getString("started_by"),
				row.getTimestamp("started_at").toInstant(), finished == null ? null : finished.toInstant());
	}

	private static LibraryRow mapLibrary(ResultSet row, int n) throws SQLException {
		java.sql.Date written = row.getDate("written_on");
		return new LibraryRow(row.getLong("id"), row.getString("title"), written == null ? null : written.toLocalDate(),
				row.getString("context"), row.getString("market_type"), row.getString("activity_code"),
				row.getBoolean("use_for_ai"), row.getString("file_name"), row.getLong("size_bytes"),
				row.getInt("text_chars"), row.getString("uploaded_by"), row.getTimestamp("uploaded_at").toInstant());
	}

	private static Timestamp ts(Instant instant) {
		return Timestamp.from(instant);
	}


}
