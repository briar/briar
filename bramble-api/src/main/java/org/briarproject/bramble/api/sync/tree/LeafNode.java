package org.briarproject.bramble.api.sync.tree;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class LeafNode extends TreeNode {

	public LeafNode(TreeHash hash, int blockNumber) {
		super(hash, 0, blockNumber, blockNumber);
	}

	@Override
	public TreeNode getLeftChild() {
		throw new UnsupportedOperationException();
	}

	@Override
	public TreeNode getRightChild() {
		throw new UnsupportedOperationException();
	}
}
