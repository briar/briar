package net.sf.briar.api.transport;

import java.util.Collection;

public interface ConnectionWindow {

	long getCentre();

	int getBitmap();

	boolean isSeen(long connection);

	void setSeen(long connection);

	Collection<Long> getUnseenConnectionNumbers();
}
