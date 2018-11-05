package org.briarproject.briar.android.conversation.glide;

import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.BriarApplication;
import org.briarproject.briar.android.conversation.AttachmentItem;

import java.io.InputStream;

@NotNullByDefault
class BriarModelLoaderFactory
		implements ModelLoaderFactory<AttachmentItem, InputStream> {

	private final BriarApplication app;

	public BriarModelLoaderFactory(BriarApplication app) {
		this.app = app;
	}

	@Override
	public ModelLoader<AttachmentItem, InputStream> build(
			MultiModelLoaderFactory multiFactory) {
		return new BriarModelLoader(app);
	}

	@Override
	public void teardown() {
		// noop
	}

}
