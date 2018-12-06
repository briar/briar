package org.briarproject.bramble.api.sync.tree;

public interface TreeHasher {

	LeafNode hashBlock(int blockNumber, byte[] data);

	ParentNode mergeTrees(TreeNode left, TreeNode right);
}
