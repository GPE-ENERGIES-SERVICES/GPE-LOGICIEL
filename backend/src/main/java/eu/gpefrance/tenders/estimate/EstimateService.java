package eu.gpefrance.tenders.estimate;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.estimate.EstimateEvent.Kind;
import eu.gpefrance.tenders.supplier.SupplierCatalog;
import eu.gpefrance.tenders.supplier.SupplierCatalog.Offer;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderNotFoundException;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * The estimates of the tenders, entered by the users. Every change locks the
 * estimate, then computes again all its figures before it is saved: the
 * totals always come from the items as they are. Only a draft may change; a
 * validated estimate is frozen, and changed through a new version, a copy.
 * Every user reads, changes and validates the estimates, like the tenders.
 * <p>
 * A cost picked from a rate of GPE or from an offer of a supplier is copied,
 * once: a later change of the rate or of the price changes no estimate,
 * unless a user asks to copy the rates again in a draft.
 */
@Service
public class EstimateService {

	private static final Logger log = LoggerFactory.getLogger(EstimateService.class);

	private final EstimateRepository estimates;

	private final EstimateEventRepository events;

	private final TenderRepository tenders;

	private final PricingSettingsRepository settings;

	private final PricingRateRepository rates;

	private final SupplierCatalog catalog;

	private final Clock clock;

	public EstimateService(EstimateRepository estimates, EstimateEventRepository events, TenderRepository tenders,
			PricingSettingsRepository settings, PricingRateRepository rates, SupplierCatalog catalog, Clock clock) {
		this.estimates = estimates;
		this.events = events;
		this.tenders = tenders;
		this.settings = settings;
		this.rates = rates;
		this.catalog = catalog;
		this.clock = clock;
	}

	/** An estimate of any tender, for the list of all estimates. */
	public record Listed(Long tenderId, String tenderTitle, EstimateResponse.Summary estimate) {
	}

	/** The estimates of every tender, the last changed first. */
	@Transactional(readOnly = true)
	public List<Listed> recent(int limit) {
		List<Estimate> found = estimates.findAll(org.springframework.data.domain.PageRequest.of(0, limit,
				org.springframework.data.domain.Sort.by("updatedAt").descending().and(
						org.springframework.data.domain.Sort.by("id").descending())))
			.getContent();
		Map<Long, String> titles = new java.util.HashMap<>();
		tenders.findAllById(found.stream().map(Estimate::getTenderId).distinct().toList())
			.forEach(tender -> titles.put(tender.getId(), tender.getTitle()));
		return found.stream()
			.map(estimate -> new Listed(estimate.getTenderId(), titles.get(estimate.getTenderId()),
					EstimateResponse.Summary.from(estimate)))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<EstimateResponse.Summary> ofTender(Long tenderId) {
		if (!tenders.existsById(tenderId)) {
			throw new TenderNotFoundException(tenderId);
		}
		return estimates.findByTender(tenderId).stream().map(EstimateResponse.Summary::from).toList();
	}

	@Transactional(readOnly = true)
	public EstimateResponse get(Long id) {
		return EstimateResponse.from(found(id));
	}

	@Transactional(readOnly = true)
	public List<EstimateEvent.Response> events(Long id) {
		found(id);
		return events.findByEstimateIdOrderByIdDesc(id).stream().map(EstimateEvent::response).toList();
	}

	/**
	 * A new draft, version after the last one of the tender or lot, with the
	 * VAT rate and the coefficients of the settings of GPE unless given.
	 */
	@Transactional
	public EstimateResponse create(Long tenderId, EstimateRequest request, String user) {
		Tender tender = tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
		if (tender.getMergedIntoId() != null) {
			throw EstimateRuleException.invalid("Ce marché a été fusionné avec le marché " + tender.getMergedIntoId()
					+ " : chiffrez celui-ci.");
		}
		String lotNumber = ItemRequest.clean(request.lotNumber());
		if (lotNumber != null && tender.getLots().stream().noneMatch(lot -> lotNumber.equals(lot.getNumber()))) {
			throw EstimateRuleException.invalid("Ce lot n'appartient pas au marché.");
		}
		ensureNoDraft(tenderId, lotNumber);
		PricingSettings defaults = settings.current();
		Map<ComponentType, java.math.BigDecimal> coefficients = new EnumMap<>(ComponentType.class);
		coefficients.putAll(defaults.getCoefficients());
		coefficients.putAll(request.coefficientsOrEmpty());
		Estimate estimate = new Estimate(tenderId, lotNumber, estimates.lastVersion(tenderId, lotNumber) + 1,
				ItemRequest.clean(request.label()),
				request.vatRate() == null ? defaults.getVatRate() : request.vatRate(), coefficients, user,
				clock.instant());
		estimate.recalculate();
		estimate = saveNew(estimate);
		record(estimate, Kind.CREATED, null, user);
		log.info("Estimate {} (tender {}, lot {}, version {}) created by {}", estimate.getId(), tenderId, lotNumber,
				estimate.getVersion(), user);
		return EstimateResponse.from(estimate);
	}

	/**
	 * A new draft copied from a version, with its items and costs as they
	 * were; the version copied stays as it is.
	 */
	@Transactional
	public EstimateResponse newVersion(Long id, String user) {
		Estimate source = found(id);
		ensureNoDraft(source.getTenderId(), source.getLotNumber());
		Estimate copy = saveNew(source.copy(estimates.lastVersion(source.getTenderId(), source.getLotNumber()) + 1,
				user, clock.instant()));
		record(copy, Kind.NEW_VERSION, "Copie de la version " + source.getVersion(), user);
		log.info("Estimate {} copied from {} by {}", copy.getId(), id, user);
		return EstimateResponse.from(copy);
	}

	/**
	 * Freezes a draft once nothing is missing: every item has its amount, every
	 * cost its value, and no item is left to check. The validated version
	 * before it, if any, is superseded.
	 */
	@Transactional
	public EstimateResponse validate(Long id, String user) {
		Estimate estimate = draft(id);
		List<EstimateItem> items = estimate.getItems();
		if (items.isEmpty()) {
			throw EstimateRuleException.invalid("Un chiffrage sans poste ne peut pas être validé.");
		}
		if (estimate.getIncompleteItems() > 0) {
			throw EstimateRuleException.invalid(estimate.getIncompleteItems()
					+ " poste(s) sans montant (coût, prix ou quantité manquant) : complétez-les avant de valider.");
		}
		long withoutCost = items.stream()
			.flatMap(item -> item.getComponents().stream())
			.filter(component -> component.getUnitCost() == null)
			.count();
		if (withoutCost > 0) {
			throw EstimateRuleException.invalid(withoutCost + " composante(s) sans coût : complétez-les avant de valider.");
		}
		long toCheck = items.stream().filter(EstimateItem::isToCheck).count();
		if (toCheck > 0) {
			throw EstimateRuleException.invalid(toCheck + " poste(s) importé(s) restent à vérifier.");
		}
		for (Estimate previous : estimates.otherValidated(estimate.getTenderId(), estimate.getLotNumber(), id)) {
			previous.supersede();
			record(previous, Kind.SUPERSEDED, "Remplacée par la version " + estimate.getVersion(), user);
		}
		estimate.validate(user, clock.instant());
		record(estimate, Kind.VALIDATED, null, user);
		log.info("Estimate {} validated by {}", id, user);
		return EstimateResponse.from(estimates.saveAndFlush(estimate));
	}

	@Transactional
	public EstimateResponse update(Long id, EstimateRequest request, String user) {
		return change(id, user, estimate -> estimate.change(ItemRequest.clean(request.label()),
				request.vatRate() == null ? estimate.getVatRate() : request.vatRate(), request.coefficientsOrEmpty(),
				user, clock.instant()));
	}

	/** Deletes a draft, with its items. */
	@Transactional
	public void delete(Long id, String user) {
		Estimate estimate = draft(id);
		estimates.delete(estimate);
		log.info("Estimate {} deleted by {}", id, user);
	}

	/** Copies again, in a draft, the current cost of each rate its costs come from. */
	@Transactional
	public EstimateResponse copyRatesAgain(Long id, String user) {
		int[] copied = { 0 };
		EstimateResponse response = change(id, user, estimate -> estimate.getItems()
			.stream()
			.flatMap(item -> item.getComponents().stream())
			.filter(component -> component.getRateCode() != null)
			.forEach(component -> rates.findByCode(component.getRateCode()).ifPresent(rate -> {
				if (component.getUnitCost() == null || component.getUnitCost().compareTo(rate.getCost()) != 0) {
					component.setUnitCost(rate.getCost());
					copied[0]++;
				}
			})));
		record(found(id), Kind.RATES_COPIED, copied[0] + " coût(s) recopié(s) depuis les taux actuels", user);
		return response;
	}

	@Transactional
	public EstimateResponse addItem(Long estimateId, ItemRequest request, String user) {
		request.check();
		return change(estimateId, user, estimate -> estimate.add(new EstimateItem(request)));
	}

	/**
	 * Several lines at once (pasted from a spreadsheet): those with an id are
	 * changed, the others added at the end; the estimate is computed once.
	 */
	@Transactional
	public EstimateResponse saveItems(Long estimateId, List<EstimateController.ItemRow> rows, String user) {
		rows.forEach(row -> row.item().check());
		return change(estimateId, user, estimate -> {
			long added = rows.stream().filter(row -> row.id() == null).count();
			if (estimate.getItems().size() + added > EstimateImportService.MAX_ITEMS) {
				throw EstimateRuleException.invalid("Un chiffrage compte au plus " + EstimateImportService.MAX_ITEMS
						+ " postes.");
			}
			for (EstimateController.ItemRow row : rows) {
				if (row.id() == null) {
					estimate.add(new EstimateItem(row.item()));
				}
				else {
					item(estimate, row.id()).change(row.item());
				}
			}
		});
	}

	@Transactional
	public EstimateResponse updateItem(Long itemId, ItemRequest request, String user) {
		request.check();
		return change(estimateOfItem(itemId), user, estimate -> item(estimate, itemId).change(request));
	}

	@Transactional
	public EstimateResponse deleteItem(Long itemId, String user) {
		return change(estimateOfItem(itemId), user, estimate -> estimate.remove(item(estimate, itemId)));
	}

	@Transactional
	public EstimateResponse reorder(Long estimateId, List<Long> itemIds, String user) {
		return change(estimateId, user, estimate -> estimate.reorder(itemIds));
	}

	@Transactional
	public EstimateResponse addComponent(Long itemId, ComponentRequest request, String user) {
		ComponentRequest resolved = resolve(request, null);
		return change(estimateOfItem(itemId), user,
				estimate -> item(estimate, itemId).add(new EstimateComponent(resolved)));
	}

	@Transactional
	public EstimateResponse updateComponent(Long componentId, ComponentRequest request, String user) {
		return change(estimateOfComponent(componentId), user, estimate -> {
			EstimateComponent component = component(estimate, componentId);
			component.change(resolve(request, component));
		});
	}

	@Transactional
	public EstimateResponse deleteComponent(Long componentId, String user) {
		return change(estimateOfComponent(componentId), user, estimate -> {
			EstimateComponent component = component(estimate, componentId);
			component.getItem().remove(component);
		});
	}

	/**
	 * Applies a change to a draft, locked, then computes it again.
	 */
	EstimateResponse change(Long estimateId, String user, Consumer<Estimate> change) {
		Estimate estimate = draft(estimateId);
		change.accept(estimate);
		estimate.touched(user, clock.instant());
		estimate.recalculate();
		return EstimateResponse.from(estimates.saveAndFlush(estimate));
	}

	void record(Estimate estimate, Kind kind, String detail, String user) {
		events.save(new EstimateEvent(estimate.getId(), kind, detail, user, clock.instant()));
	}

	Estimate found(Long id) {
		return estimates.findById(id).orElseThrow(EstimateService::notFound);
	}

	EstimateItem item(Long itemId) {
		return item(found(estimateOfItem(itemId)), itemId);
	}

	/**
	 * The cost to save: copied from the rate or the offer picked, when it is
	 * new or another one; as sent otherwise.
	 */
	private ComponentRequest resolve(ComponentRequest request, EstimateComponent previous) {
		if (request.offerId() != null && (previous == null || !request.offerId().equals(previous.getOfferId()))) {
			Offer offer = catalog.find(request.offerId())
				.orElseThrow(() -> EstimateRuleException.invalid("Offre fournisseur introuvable."));
			return request.withOffer(offer.id(), offer.supplierName(), offer.supplierRef(), offer.manufacturerRef(),
					offer.purchaseUnit(), offer.unitCost(),
					offer.validFrom() != null ? offer.validFrom()
							: offer.importedAt().atZone(clock.getZone()).toLocalDate());
		}
		String code = ItemRequest.clean(request.rateCode());
		if (code != null && (previous == null || !code.equalsIgnoreCase(previous.getRateCode()))) {
			PricingRate rate = rates.findByCode(code.toUpperCase())
				.filter(PricingRate::isActive)
				.orElseThrow(() -> EstimateRuleException.invalid("Taux « " + code + " » introuvable ou inactif."));
			return request.withRate(rate);
		}
		return request;
	}

	private void ensureNoDraft(Long tenderId, String lotNumber) {
		if (estimates.hasDraft(tenderId, lotNumber)) {
			throw EstimateRuleException.conflict("Un chiffrage en cours existe déjà pour ce marché ou ce lot.");
		}
	}

	private Estimate saveNew(Estimate estimate) {
		try {
			return estimates.saveAndFlush(estimate);
		}
		catch (DataIntegrityViolationException ex) {
			// Another draft created at the same time.
			throw EstimateRuleException.conflict("Un chiffrage en cours existe déjà pour ce marché ou ce lot.");
		}
	}

	private Estimate draft(Long id) {
		Estimate estimate = estimates.lockById(id).orElseThrow(EstimateService::notFound);
		if (!estimate.isDraft()) {
			throw EstimateRuleException.conflict("Ce chiffrage est figé : créez-en une nouvelle version pour le modifier.");
		}
		return estimate;
	}

	private Long estimateOfItem(Long itemId) {
		return estimates.estimateOfItem(itemId)
			.orElseThrow(() -> EstimateRuleException.notFound("Poste introuvable."));
	}

	private Long estimateOfComponent(Long componentId) {
		return estimates.estimateOfComponent(componentId)
			.orElseThrow(() -> EstimateRuleException.notFound("Composante introuvable."));
	}

	private static EstimateItem item(Estimate estimate, Long itemId) {
		return estimate.getItems()
			.stream()
			.filter(item -> itemId.equals(item.getId()))
			.findFirst()
			.orElseThrow(() -> EstimateRuleException.notFound("Poste introuvable."));
	}

	private static EstimateComponent component(Estimate estimate, Long componentId) {
		return estimate.getItems()
			.stream()
			.flatMap(item -> item.getComponents().stream())
			.filter(component -> componentId.equals(component.getId()))
			.findFirst()
			.orElseThrow(() -> EstimateRuleException.notFound("Composante introuvable."));
	}

	private static EstimateRuleException notFound() {
		return EstimateRuleException.notFound("Chiffrage introuvable.");
	}

}
