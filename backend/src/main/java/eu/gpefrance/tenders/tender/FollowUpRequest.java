package eu.gpefrance.tenders.tender;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param comment the new comment; {@code null} keeps the current one and a
 * blank text removes it
 */
public record FollowUpRequest(@NotNull FollowUpStatus status, @Size(max = 2000) String comment) {

	String commentOr(String current) {
		if (comment == null) {
			return current;
		}
		return comment.isBlank() ? null : comment.strip();
	}

}
