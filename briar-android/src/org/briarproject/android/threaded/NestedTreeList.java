package org.briarproject.android.threaded;

import android.support.annotation.UiThread;

import org.briarproject.api.clients.MessageTree;
import org.briarproject.clients.MessageTreeImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

@UiThread
public class NestedTreeList<T extends MessageTree.MessageNode>
		implements Iterable<T> {

	private final MessageTree<T> tree = new MessageTreeImpl<>();
	private List<T> depthFirstCollection = new ArrayList<>();

	public void addAll(Collection<T> collection) {
		tree.add(collection);
		depthFirstCollection = new ArrayList<>(tree.depthFirstOrder());
	}

	public void add(T elem) {
		tree.add(elem);
		depthFirstCollection = new ArrayList<>(tree.depthFirstOrder());
	}

	public void clear() {
		tree.clear();
		depthFirstCollection.clear();
	}

	public T get(int index) {
		return depthFirstCollection.get(index);
	}

	public int indexOf(T elem) {
		return depthFirstCollection.indexOf(elem);
	}

	public int size() {
		return depthFirstCollection.size();
	}

	@Override
	public Iterator<T> iterator() {
		return depthFirstCollection.iterator();
	}
}
