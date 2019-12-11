package org.briarproject.briar.android.attachment;

import android.content.res.AssetManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static android.os.Build.VERSION.SDK_INT;
import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static java.util.logging.Logger.getLogger;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class AttachmentCreationTaskTest {

	private static Logger LOG =
			getLogger(AttachmentCreationTaskTest.class.getName());

	private final ImageHelper imageHelper = new ImageHelperImpl();
	private final ImageSizeCalculator imageSizeCalculator =
			new ImageSizeCalculator(imageHelper);
	@SuppressWarnings("ConstantConditions")  // add real objects when needed
	private final AttachmentCreationTask task = new AttachmentCreationTask(null,
			getApplicationContext().getContentResolver(), null,
			imageSizeCalculator, null, null, true);

	@Test
	public void testCompress100x100Png() throws Exception {
		testCompress("red-100x100.png", "image/png");
	}

	@Test
	public void testCompress500x500Png() throws Exception {
		testCompress("blue-500x500.png", "image/png");
	}

	@Test
	public void testCompress1000x2000Png() throws Exception {
		testCompress("green-1000x2000.png", "image/png");
	}

	@Test
	public void testCompressVeryHighJpg() throws Exception {
		testCompress("error_high.jpg", "image/jpeg");
	}

	@Test
	public void testCompressVeryWideJpg() throws Exception {
		testCompress("error_wide.jpg", "image/jpeg");
	}

	@Test
	public void testCompressAnimatedGif1() throws Exception {
		// TODO: Remove this assumption when we support large messages
		assumeTrue(SDK_INT >= 24);
		testCompress("animated.gif", "image/gif");
	}

	@Test
	public void testCompressAnimatedGif2() throws Exception {
		// TODO: Remove this assumption when we support large messages
		assumeTrue(SDK_INT >= 24);
		testCompress("animated2.gif", "image/gif");
	}

	@Test
	public void testCompressVeryLargeGif() throws Exception {
		// TODO: Remove this assumption when we support large messages
		assumeTrue(SDK_INT >= 24);
		testCompress("error_large.gif", "image/gif");
	}

	private void testCompress(String filename, String contentType)
			throws Exception {
		InputStream is = getAssetInputStream(filename);
		task.compressImage(is, contentType);
	}

	@Test
	public void testPngSuiteCompress() throws Exception {
		for (String file : getAssetFiles("PngSuite")) {
			if (file.endsWith(".png")) {
				InputStream is = getAssetInputStream("PngSuite/" + file);
				try {
					task.compressImage(is, "image/png");
					LOG.warning("PASS: " + file);
				} catch (IOException e) {
					LOG.warning("ERROR: " + file);
				}
			}
		}
	}

	private InputStream getAssetInputStream(String name) throws IOException {
		LOG.info("getAssetInputStream: " + name);
		return getAssetManager().open(name);
	}

	private String[] getAssetFiles(String path) throws IOException {
		return getAssetManager().list(path);
	}

	private AssetManager getAssetManager() {
		// pm.getResourcesForApplication(packageName).getAssets() did not work
		//noinspection deprecation
		return getContext().getAssets();
	}

}
