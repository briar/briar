package net.sf.briar.api.transport;

public interface ConnectionWindow {

	long getCentre();

	int getBitmap();

	boolean isSeen(long connectionNumber);

	void setSeen(long connectionNumber);
}
