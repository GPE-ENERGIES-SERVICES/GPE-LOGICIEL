package eu.gpefrance.tenders.tender;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FollowUpRequest(@NotNull FollowUpStatus status, @Size(max = 2000) String comment) {

	String cleanComment() {
		return comment == null || comment.isBlank() ? null : comment.strip();
	}

}
