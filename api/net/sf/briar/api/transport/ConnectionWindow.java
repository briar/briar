package net.sf.briar.api.transport;

import java.util.Collection;

public interface ConnectionWindow {

	boolean isSeen(long connection);

	void setSeen(long connection);

	Collection<Long> getUnseen();
}
