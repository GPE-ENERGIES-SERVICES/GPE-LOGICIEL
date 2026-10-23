package eu.gpefrance.tenders.document;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The quota of the retrieved consultation files ({@code dce.retrieval.quota},
 * in bytes): the bytes written by the retrieved dossiers
 * ({@code dossier.stored_bytes}) plus the space reserved by the retrievals in
 * progress ({@code dce_storage_reservation}). A retrieval reserves the most
 * it may write before it starts; checking the quota and reserving happen
 * under one PostgreSQL advisory lock, so that workers in parallel, even in
 * several processes, never reserve together more than the quota. A
 * reservation is released at the end of the retrieval, whatever happens, and
 * those of the retrievals interrupted by a stop are released at the next
 * start. Nothing already kept is ever deleted to make room.
 */
@Component
public class DceStorageQuota {

	/** Key of the advisory lock of the quota. */
	private static final long LOCK = 7_271_001L;

	private static final Logger log = LoggerFactory.getLogger(DceStorageQuota.class);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transaction;

	private final DceRetrievalProperties properties;

	private final Clock clock;

	public DceStorageQuota(JdbcTemplate jdbc, TransactionTemplate transaction, DceRetrievalProperties properties,
			Clock clock) {
		this.jdbc = jdbc;
		this.transaction = transaction;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Reserves space for the retrieval of a link.
	 * @return whether the space was reserved; false when the quota would be
	 * exceeded
	 */
	public boolean reserve(Long linkId, long bytes) {
		Boolean reserved = transaction.execute(status -> {
			lock();
			long used = used();
			long alreadyReserved = reserved();
			if (used + alreadyReserved + bytes > properties.quota().toBytes()) {
				log.warn("DCE quota: {} bytes refused to link {} ({} used, {} reserved, quota {})", bytes, linkId, used,
						alreadyReserved, properties.quota().toBytes());
				return false;
			}
			jdbc.update("""
					INSERT INTO dce_storage_reservation (dce_link_id, bytes, reserved_at) VALUES (?, ?, ?)
					ON CONFLICT (dce_link_id) DO UPDATE SET bytes = EXCLUDED.bytes, reserved_at = EXCLUDED.reserved_at
					""", linkId, bytes, java.sql.Timestamp.from(clock.instant()));
			return true;
		});
		return Boolean.TRUE.equals(reserved);
	}

	/**
	 * Checks, under the lock, that the files written by a retrieval fit in the
	 * quota with what is already kept and the other reservations; the
	 * reservation of the link no longer counts.
	 */
	public boolean fits(Long linkId, long writtenBytes) {
		Boolean fits = transaction.execute(status -> {
			lock();
			long others = jdbc.queryForObject(
					"SELECT coalesce(sum(bytes), 0) FROM dce_storage_reservation WHERE dce_link_id <> ?", Long.class,
					linkId);
			return used() + others + writtenBytes <= properties.quota().toBytes();
		});
		return Boolean.TRUE.equals(fits);
	}

	public void release(Long linkId) {
		jdbc.update("DELETE FROM dce_storage_reservation WHERE dce_link_id = ?", linkId);
	}

	/**
	 * Releases the reservations of the links that are no longer being
	 * retrieved (stop of the application during a retrieval).
	 * @return the number released
	 */
	public int releaseAbandoned() {
		return jdbc.update("""
				DELETE FROM dce_storage_reservation r
				WHERE NOT EXISTS (SELECT 1 FROM dce_link l WHERE l.id = r.dce_link_id AND l.status = 'IN_PROGRESS')
				""");
	}

	/** Bytes written by the retrieved dossiers. */
	public long used() {
		return jdbc.queryForObject(
				"SELECT coalesce(sum(stored_bytes), 0) FROM dossier WHERE origin = 'RETRIEVED'", Long.class);
	}

	/** Bytes reserved by the retrievals in progress. */
	public long reserved() {
		return jdbc.queryForObject("SELECT coalesce(sum(bytes), 0) FROM dce_storage_reservation", Long.class);
	}

	public long quota() {
		return properties.quota().toBytes();
	}

	private void lock() {
		jdbc.queryForObject("SELECT pg_advisory_xact_lock(?)::text", String.class, LOCK);
	}

}
