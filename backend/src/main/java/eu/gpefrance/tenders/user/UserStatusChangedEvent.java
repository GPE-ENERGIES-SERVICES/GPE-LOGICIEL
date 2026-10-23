package eu.gpefrance.tenders.user;

/**
 * An account was disabled or enabled again: what depends on its activity
 * (its saved searches, for the eligibility of the consultation files) is
 * reviewed once the change is committed.
 */
public record UserStatusChangedEvent(Long userId, boolean enabled) {
}
