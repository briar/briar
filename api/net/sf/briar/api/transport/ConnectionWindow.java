package net.sf.briar.api.transport;

import java.util.Collection;

public interface ConnectionWindow {

	long getCentre();

	int getBitmap();

	boolean isSeen(long connectionNumber);

	void setSeen(long connectionNumber);

	Collection<Long> getUnseenConnectionNumbers();
}
