package org.briarproject.bramble.api.sync.tree;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

@NotNullByDefault
public interface TreeHasher {

	LeafNode hashBlock(int blockNumber, byte[] data);

	ParentNode mergeTrees(TreeNode left, TreeNode right);
}
