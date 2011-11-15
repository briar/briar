package net.sf.briar.api.transport;

import java.util.Collection;

public interface ConnectionWindowFactory {

	ConnectionWindow createConnectionWindow();

	ConnectionWindow createConnectionWindow(Collection<Long> unseen);
}
