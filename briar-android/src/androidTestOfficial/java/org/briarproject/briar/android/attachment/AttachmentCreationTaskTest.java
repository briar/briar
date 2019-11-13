package org.briarproject.briar.android.attachment;

import android.content.res.AssetManager;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static androidx.test.InstrumentationRegistry.getContext;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static java.util.logging.Logger.getLogger;

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
	public void testCompress() throws Exception {
		InputStream is = getAssetInputStream("animated.gif");
		task.compressImage(is, "image/gif");

		is = getAssetInputStream("animated2.gif");
		task.compressImage(is, "image/gif");

		is = getAssetInputStream("blue-500x500.png");
		task.compressImage(is, "image/png");

		is = getAssetInputStream("error_high.jpg");
		task.compressImage(is, "image/jpeg");

		is = getAssetInputStream("error_large.gif");
		task.compressImage(is, "image/gif");

		is = getAssetInputStream("error_wide.jpg");
		task.compressImage(is, "image/jpeg");

		is = getAssetInputStream("green-1000x2000.png");
		task.compressImage(is, "image/png");

		is = getAssetInputStream("red-100x100.png");
		task.compressImage(is, "image/png");
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
