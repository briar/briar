package org.briarproject.bramble.api.sync.tree;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public class ParentNode extends TreeNode {

	private final TreeNode left, right;

	public ParentNode(TreeHash hash, TreeNode left, TreeNode right) {
		super(hash, Math.max(left.getHeight(), right.getHeight()) + 1,
				left.getFirstBlockNumber(), right.getLastBlockNumber());
		this.left = left;
		this.right = right;
	}

	@Override
	public TreeNode getLeftChild() {
		return left;
	}

	@Override
	public TreeNode getRightChild() {
		return right;
	}
}
