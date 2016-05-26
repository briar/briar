package org.briarproject.api.clients;

import org.briarproject.api.sync.MessageId;

import java.util.Collection;
import java.util.Comparator;

public interface MessageTree<T extends MessageTree.MessageNode> {

	void add(Collection<T> nodes);
	void clear();
	Collection<T> depthFirstOrder();
	void setComparator(Comparator<T> comparator);

	interface MessageNode {
		MessageId getId();
		MessageId getParentId();
		long getTimestamp();
	}

}
