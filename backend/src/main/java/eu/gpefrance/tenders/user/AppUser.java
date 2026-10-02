package eu.gpefrance.tenders.user;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String email;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Role role;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AppUser() {
	}

	public AppUser(String email, String displayName, String passwordHash, Role role, Instant now) {
		this.email = normalizeEmail(email);
		this.displayName = displayName.strip();
		this.passwordHash = passwordHash;
		this.role = role;
		this.createdAt = now;
	}

	public static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	public void update(String displayName, Role role, boolean enabled) {
		this.displayName = displayName.strip();
		this.role = role;
		this.enabled = enabled;
	}

	public void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
