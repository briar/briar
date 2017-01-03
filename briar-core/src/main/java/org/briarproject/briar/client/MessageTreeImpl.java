package org.briarproject.briar.client;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.client.MessageTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
public class MessageTreeImpl<T extends MessageTree.MessageNode>
		implements MessageTree<T> {

	private final Map<MessageId, List<T>> nodeMap =
			new HashMap<MessageId, List<T>>();
	private final List<T> roots = new ArrayList<T>();
	private final List<List<T>> unsortedLists = new ArrayList<List<T>>();

	private Comparator<T> comparator = new Comparator<T>() {
		@Override
		public int compare(T o1, T o2) {
			return Long.valueOf(o1.getTimestamp()).compareTo(o2.getTimestamp());
		}
	};

	@Override
	public synchronized void clear() {
		roots.clear();
		nodeMap.clear();
	}

	@Override
	public synchronized void add(Collection<T> nodes) {
		// add all nodes to the node map
		for (T node : nodes) {
			nodeMap.put(node.getId(), new ArrayList<T>());
		}
		// parse the nodes for dependencies
		for (T node : nodes) {
			parseNode(node);
		}
		sortUnsorted();
	}

	@Override
	public synchronized void add(T node) {
		add(Collections.singletonList(node));
	}

	private void markAsUnsorted(List<T> list) {
		if (!unsortedLists.contains(list))
			unsortedLists.add(list);
	}

	private void parseNode(T node) {
		if (node.getParentId() == null) {
			roots.add(node);
			markAsUnsorted(roots);
		} else {
			// retrieve the parent's children
			List<T> pChildren = nodeMap.get(node.getParentId());
			pChildren.add(node);
			markAsUnsorted(pChildren);
		}
	}

	private void sortUnsorted() {
		for (List<T> list : unsortedLists) {
			Collections.sort(list, comparator);
		}
		unsortedLists.clear();
	}

	private void traverse(List<T> list, T node, int level) {
		list.add(node);
		List<T> children = nodeMap.get(node.getId());
		node.setLevel(level);
		for (T child : children) {
			traverse(list, child, level + 1);
		}
	}

	@Override
	public synchronized void setComparator(Comparator<T> comparator) {
		this.comparator = comparator;
		// Sort all lists with the new comparator
		Collections.sort(roots, comparator);
		for (Map.Entry<MessageId, List<T>> entry : nodeMap.entrySet()) {
			Collections.sort(entry.getValue(), comparator);
		}
	}

	@Override
	public synchronized Collection<T> depthFirstOrder() {
		List<T> orderedList = new ArrayList<T>();
		for (T root : roots) {
			traverse(orderedList, root, 0);
		}
		return orderedList;
	}

}
