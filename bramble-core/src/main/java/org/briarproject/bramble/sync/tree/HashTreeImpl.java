package org.briarproject.bramble.sync.tree;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.tree.LeafNode;
import org.briarproject.bramble.api.sync.tree.TreeHasher;
import org.briarproject.bramble.api.sync.tree.TreeNode;

import java.util.Deque;
import java.util.LinkedList;

import javax.inject.Inject;

@NotNullByDefault
class HashTreeImpl implements HashTree {

	private final TreeHasher treeHasher;
	private final Deque<TreeNode> nodes = new LinkedList<>();

	@Inject
	HashTreeImpl(TreeHasher treeHasher) {
		this.treeHasher = treeHasher;
	}

	@Override
	public void addLeaf(LeafNode leaf) {
		TreeNode add = leaf;
		int height = leaf.getHeight();
		TreeNode last = nodes.peekLast();
		while (last != null && last.getHeight() == height) {
			add = treeHasher.mergeTrees(last, add);
			height = add.getHeight();
			nodes.removeLast();
			last = nodes.peekLast();
		}
		nodes.addLast(add);
	}

	@Override
	public TreeNode getRoot() {
		TreeNode root = nodes.removeLast();
		while (!nodes.isEmpty()) {
			root = treeHasher.mergeTrees(nodes.removeLast(), root);
		}
		return root;
	}
}
