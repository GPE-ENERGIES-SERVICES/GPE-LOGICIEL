package eu.gpefrance.tenders.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * First administrator account, created at startup when no account exists.
 */
@ConfigurationProperties("bootstrap-admin")
public record BootstrapAdminProperties(String email, String password, String name) {
}
