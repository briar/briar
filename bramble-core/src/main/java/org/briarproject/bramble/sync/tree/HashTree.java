package org.briarproject.bramble.sync.tree;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.tree.LeafNode;
import org.briarproject.bramble.api.sync.tree.TreeNode;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
interface HashTree {

	void addLeaf(LeafNode leaf);

	TreeNode getRoot();
}
