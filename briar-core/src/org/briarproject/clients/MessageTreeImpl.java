package org.briarproject.clients;

import org.briarproject.api.clients.MessageTree;
import org.briarproject.api.sync.MessageId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageTreeImpl<T extends MessageTree.MessageNode>
		implements MessageTree<T> {

	private final Map<MessageId, List<T>> nodeMap = new HashMap<MessageId, List<T>>();
	private final List<T> roots = new ArrayList<T>();
	private final List<List<T>> unsortedLists = new ArrayList<List<T>>();

	private Comparator<T> comparator = new Comparator<T>() {
		@Override
		public int compare(T o1, T o2) {
			return Long.valueOf(o1.getTimestamp()).compareTo(o2.getTimestamp());
		}
	};

	@Override
	public void clear() {
		roots.clear();
		nodeMap.clear();
	}

	@Override
	public void add(Collection<T> nodes) {
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
	public void add(T node) {
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
		// leave unsorted if there is no comparator
		if (comparator != null) {
			for (List<T> list : unsortedLists) {
				Collections.sort(list, comparator);
			}
			unsortedLists.clear();
		}
	}


	private void traverse(List<T> list, T node) {
		list.add(node);
		for (T child : nodeMap.get(node.getId())) {
			traverse(list, child);
		}
	}

	@Override
	public void setComparator(Comparator<T> comparator) {
		this.comparator = comparator;
		// Sort all lists with the new comparator
		Collections.sort(roots, comparator);
		for (Map.Entry<MessageId, List<T>> entry: nodeMap.entrySet()) {
			Collections.sort(entry.getValue(), comparator);
		}
	}

	@Override
	public Collection<T> depthFirstOrder() {
		List<T> orderedList = new ArrayList<T>();
		for (T root : roots) {
			traverse(orderedList, root);
		}
		return Collections.unmodifiableList(orderedList);
	}

}
