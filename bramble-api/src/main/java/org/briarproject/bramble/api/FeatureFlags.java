package org.briarproject.bramble.api;

/**
 * Interface for specifying which features are enabled in a build.
 */
public interface FeatureFlags {

	boolean shouldEnableImageAttachments();

	boolean shouldEnableProfilePictures();

	boolean shouldEnableDisappearingMessages();

	boolean shouldEnableTransferData();

	boolean shouldEnableShareAppViaOfflineHotspot();
}
