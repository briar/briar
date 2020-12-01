package org.briarproject.briar.android.attachment;

import android.app.Application;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

import static org.briarproject.briar.android.attachment.AttachmentDimensions.getAttachmentDimensions;

@Module
public class AttachmentModule {

	@Provides
	ImageHelper provideImageHelper(ImageHelperImpl imageHelper) {
		return imageHelper;
	}

	@Provides
	ImageSizeCalculator provideImageSizeCalculator(ImageHelper imageHelper) {
		return new ImageSizeCalculator(imageHelper);
	}

	@Provides
	ImageCompressor provideImageCompressor(
			ImageCompressorImpl imageCompressor) {
		return imageCompressor;
	}

	@Provides
	AttachmentDimensions provideAttachmentDimensions(Application app) {
		return getAttachmentDimensions(app.getResources());
	}

	@Provides
	@Singleton
	AttachmentRetriever provideAttachmentRetriever(
			AttachmentRetrieverImpl attachmentRetriever) {
		return attachmentRetriever;
	}

	@Provides
	@Singleton
	AttachmentCreator provideAttachmentCreator(
			AttachmentCreatorImpl attachmentCreator) {
		return attachmentCreator;
	}
}
