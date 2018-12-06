package org.briarproject.bramble.api.sync.tree;

public class ParentNode extends TreeNode {

	public ParentNode(TreeHash hash, TreeNode left, TreeNode right) {
		super(hash, Math.max(left.getHeight(), right.getHeight()) + 1,
				left.getFirstBlockNumber(), right.getLastBlockNumber());
	}
}
