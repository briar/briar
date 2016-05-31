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

	Map<MessageId, List<T>> nodeMap = new HashMap<MessageId, List<T>>();
	List<T> roots = new ArrayList<T>();

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
			if (node.getParentId() == null) {
				roots.add(node);
			}
			else {
				// retrieve the parent's children
				List<T> pChildren = nodeMap.get(node.getParentId());
				pChildren.add(node);
			}
		}
		sortAll();
	}

	private void sortAll() {
		Collections.sort(roots, comparator);
		// Sort all the sub-lists
		for (Map.Entry<MessageId, List<T>> entry: nodeMap.entrySet()) {
			Collections.sort(entry.getValue(), comparator);
		}
	}

	private void traverse(List<T> list, T node) {
		list.add(node);
		for (T child : nodeMap.get(node.getId())) {
			traverse(list, child);
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

	@Override
	public void setComparator(Comparator<T> comparator) {
		this.comparator = comparator;
	}

}
