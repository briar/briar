package org.briarproject.bramble.api.sync.tree;

public class LeafNode extends TreeNode {

	public LeafNode(TreeHash hash, int blockNumber) {
		super(hash, 0, blockNumber, blockNumber);
	}
}
