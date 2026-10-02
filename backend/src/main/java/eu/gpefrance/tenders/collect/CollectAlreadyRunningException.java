package eu.gpefrance.tenders.collect;

public class CollectAlreadyRunningException extends RuntimeException {

	public CollectAlreadyRunningException() {
		super("A collection is already running");
	}

}
