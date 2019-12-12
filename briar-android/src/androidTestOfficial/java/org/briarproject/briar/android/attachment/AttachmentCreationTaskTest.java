package org.briarproject.briar.android.attachment;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.logging.Logger;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static android.os.Build.VERSION.SDK_INT;
import static java.util.logging.Logger.getLogger;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class AttachmentCreationTaskTest
		extends AbstractAttachmentCreationTaskTest {

	private static Logger LOG =
			getLogger(AttachmentCreationTaskTest.class.getName());

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
}
