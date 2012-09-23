package net.sf.briar.api.transport;

import java.util.Set;

public interface ConnectionWindow {

	boolean isSeen(long connection);

	void setSeen(long connection);

	Set<Long> getUnseen();
}
