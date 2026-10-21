package eu.gpefrance.tenders.collect.approch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import eu.gpefrance.tenders.collect.CollectProperties;
import eu.gpefrance.tenders.collect.HttpRetry;
import eu.gpefrance.tenders.collect.SourceRecord;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads the purchase projects of APProch from their open data export
 * (Opendatasoft Explore v2.1, Licence Ouverte 2.0), all of them in one
 * request: the dataset lists the projects open today, without publication
 * date, and its 10,000 results limit of paged reading is already exceeded.
 * <p>
 * A project is kept with the day it was read and the name of its buyer,
 * which the dataset only gives by its SIREN: the name comes from the public
 * company search API, asked once per SIREN while the application runs. Its
 * identifier is its APProch code followed by a digest of what it says: a
 * changed project is stored again as a new version, an unchanged one is
 * skipped.
 */
@Component
public class ApprochClient {

	/** The fields read by {@link ApprochNoticeParser}, in the order of the digest. */
	static final List<String> FIELDS = List.of("code", "libelle", "statut", "description",
			"siren_de_l_entite_acheteuse", "categorie_d_achat", "code_s_cpv", "date_previsionnelle_de_publication",
			"date_cible_de_remise_des_offres", "type_de_procedure", "lien_vers_la_consultation",
			"montant_estime_du_marche", "duree_previsionnelle_du_marche", "departement_s_d_execution_du_marche");

	/** Pause between two requests to the company search API, well below its limit of 7 per second. */
	private static final Duration NAME_PAUSE = Duration.ofMillis(200);

	private final RestClient projects;

	private final RestClient companies;

	private final Duration retryDelay;

	/** Name of each SIREN already asked, empty when the SIREN is unknown. */
	private final Map<String, Optional<String>> buyerNames = new ConcurrentHashMap<>();

	public ApprochClient(RestClient.Builder builder, ApprochProperties properties, CollectProperties collect) {
		this.projects = builder.clone().baseUrl(properties.baseUrl()).build();
		this.companies = builder.clone().baseUrl(properties.companiesUrl()).build();
		this.retryDelay = collect.retryDelay();
	}

	/**
	 * Every project listed today. Throws when the export or the name of a
	 * buyer cannot be read: the collection then fails and is tried again.
	 */
	public List<SourceRecord> fetchCurrent(LocalDate today) {
		JsonNode export = HttpRetry.once("APProch export", retryDelay, this::fetchExport);
		List<SourceRecord> records = new ArrayList<>();
		for (JsonNode project : export.values()) {
			records.add(toRecord(project, today));
		}
		return records;
	}

	private JsonNode fetchExport() {
		JsonNode export = projects.get()
			.uri(uri -> uri.path("/exports/json")
				.queryParam("select", String.join(",", FIELDS))
				.queryParam("order_by", "code")
				.build())
			.retrieve()
			.body(JsonNode.class);
		if (export == null || !export.isArray()) {
			throw new IllegalStateException("Unexpected response from APProch: no list of projects");
		}
		return export;
	}

	/**
	 * A project without code is returned all the same: the collection rejects
	 * it without stopping.
	 */
	private SourceRecord toRecord(JsonNode project, LocalDate today) {
		String code = text(project.get("code"));
		String siren = text(project.get("siren_de_l_entite_acheteuse"));
		String buyerName = siren == null ? null : buyerName(siren);
		ObjectNode payload = JsonNodeFactory.instance.objectNode();
		payload.put("collectedOn", today.toString());
		payload.put("buyerName", buyerName);
		payload.set("project", project);
		return new SourceRecord(code == null ? null : code + "-" + digest(project, buyerName), today,
				payload.toString());
	}

	private String buyerName(String siren) {
		return buyerNames.computeIfAbsent(siren, key -> {
			pause();
			JsonNode found = HttpRetry.once("Name of the SIREN " + key, retryDelay, () -> companies.get()
				.uri(uri -> uri.path("/search").queryParam("q", key).queryParam("per_page", 1).build())
				.retrieve()
				.body(JsonNode.class));
			if (found == null) {
				throw new IllegalStateException("Empty response from the company search API for " + key);
			}
			for (JsonNode company : found.path("results").values()) {
				if (key.equals(text(company.get("siren")))) {
					return Optional.ofNullable(text(company.get("nom_complet")));
				}
			}
			return Optional.empty();
		}).orElse(null);
	}

	/**
	 * The digest of the fields read and of the name of the buyer: it changes
	 * only when what the project says changes.
	 */
	static String digest(JsonNode project, String buyerName) {
		StringBuilder content = new StringBuilder();
		for (String field : FIELDS) {
			content.append(field).append('=').append(text(project.get(field))).append('\n');
		}
		content.append("buyer=").append(buyerName);
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(content.toString().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash, 0, 6);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String text(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		String text = node.asString().strip();
		return text.isEmpty() ? null : text;
	}

	private static void pause() {
		try {
			Thread.sleep(NAME_PAUSE);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while reading the names of the buyers", ex);
		}
	}

}
