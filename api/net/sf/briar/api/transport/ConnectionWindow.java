package net.sf.briar.api.transport;

import java.util.Map;

public interface ConnectionWindow {

	boolean isSeen(long connection);

	void setSeen(long connection);

	Map<Long, byte[]> getUnseen();
}
