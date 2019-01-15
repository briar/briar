package org.briarproject.briar.android.conversation;

import android.content.res.AssetManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.briarproject.bramble.api.UniqueId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.AttachmentHeader;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class AttachmentControllerIntegrationTest {

	private static final String smallKitten =
			"https://upload.wikimedia.org/wikipedia/commons/thumb/0/06/Kitten_in_Rizal_Park%2C_Manila.jpg/160px-Kitten_in_Rizal_Park%2C_Manila.jpg";
	private static final String originalKitten =
			"https://upload.wikimedia.org/wikipedia/commons/0/06/Kitten_in_Rizal_Park%2C_Manila.jpg";
	private static final String pngKitten =
			"https://upload.wikimedia.org/wikipedia/commons/c/c8/Young_cat.png";
	private static final String uberGif =
			"https://raw.githubusercontent.com/fuzzdb-project/fuzzdb/master/attack/file-upload/malicious-images/uber.gif";
	private static final String lottaPixel =
			"https://raw.githubusercontent.com/fuzzdb-project/fuzzdb/master/attack/file-upload/malicious-images/lottapixel.jpg";
	private static final String imageIoCrash =
			"https://www.landaire.net/img/crasher.png";
	private static final String gimpCrash =
			"https://gitlab.gnome.org/GNOME/gimp/uploads/75f5b7ed3b09b3f1c13f1f65bffe784f/31153c919d3aa634e8e6cff82219fe7352dd8a37.png";
	private static final String optiPngAfl =
			"https://sourceforge.net/p/optipng/bugs/64/attachment/test.gif";
	private static final String librawError =
			"https://www.libraw.org/sites/libraw.org/files/P1010671.JPG";

	private final AttachmentDimensions dimensions = new AttachmentDimensions(
			100, 50, 200, 75, 300
	);
	private final MessageId msgId = new MessageId(getRandomId());

	private final AttachmentController controller =
			new AttachmentController(null, dimensions);

	@Test
	public void testSmallJpegImage() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(smallKitten);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(msgId, item.getMessageId());
		assertEquals(160, item.getWidth());
		assertEquals(240, item.getHeight());
		assertEquals(160, item.getThumbnailWidth());
		assertEquals(240, item.getThumbnailHeight());
		assertEquals("image/jpeg", item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testBigJpegImage() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(originalKitten);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(msgId, item.getMessageId());
		assertEquals(1728, item.getWidth());
		assertEquals(2592, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxHeight, item.getThumbnailHeight());
		assertEquals("image/jpeg", item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testSmallPngImage() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/png");
		InputStream is = getUrlInputStream(pngKitten);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(msgId, item.getMessageId());
		assertEquals(737, item.getWidth());
		assertEquals(510, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(138, item.getThumbnailHeight());
		assertEquals("image/png", item.getMimeType());
		assertEquals("png", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testUberGif() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(uberGif);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(1, item.getWidth());
		assertEquals(1, item.getHeight());
		assertEquals(dimensions.minHeight, item.getThumbnailWidth());
		assertEquals(dimensions.minHeight, item.getThumbnailHeight());
		assertEquals("image/gif", item.getMimeType());
		assertEquals("gif", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testLottaPixels() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(lottaPixel);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(64250, item.getWidth());
		assertEquals(64250, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxWidth, item.getThumbnailHeight());
		assertEquals("image/jpeg", item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testImageIoCrash() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(imageIoCrash);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(1184, item.getWidth());
		assertEquals(448, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.minHeight, item.getThumbnailHeight());
		assertEquals("image/png", item.getMimeType());
		assertEquals("png", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testGimpCrash() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(gimpCrash);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(1, item.getWidth());
		assertEquals(1, item.getHeight());
		assertEquals(dimensions.minHeight, item.getThumbnailWidth());
		assertEquals(dimensions.minHeight, item.getThumbnailHeight());
		assertEquals("image/gif", item.getMimeType());
		assertEquals("gif", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testOptiPngAfl() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(optiPngAfl);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(32, item.getWidth());
		assertEquals(32, item.getHeight());
		assertEquals(dimensions.minHeight, item.getThumbnailWidth());
		assertEquals(dimensions.minHeight, item.getThumbnailHeight());
		assertEquals("image/gif", item.getMimeType());
		assertEquals("gif", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testLibrawError() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getUrlInputStream(librawError);
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertTrue(item.hasError());
	}

	@Test
	public void testSmallAnimatedGifMaxDimensions() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/gif");
		InputStream is = getAssetInputStream("animated.gif");
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(65535, item.getWidth());
		assertEquals(65535, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxWidth, item.getThumbnailHeight());
		assertEquals("image/gif", item.getMimeType());
		assertEquals("gif", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testSmallAnimatedGifHugeDimensions() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/gif");
		InputStream is = getAssetInputStream("animated2.gif");
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(10000, item.getWidth());
		assertEquals(10000, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxWidth, item.getThumbnailHeight());
		assertEquals("image/gif", item.getMimeType());
		assertEquals("gif", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testSmallGifLargeDimensions() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/gif");
		InputStream is = getAssetInputStream("error_large.gif");
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(16384, item.getWidth());
		assertEquals(16384, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxWidth, item.getThumbnailHeight());
		assertEquals("image/gif", item.getMimeType());
		assertEquals("gif", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testHighError() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getAssetInputStream("error_high.jpg");
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(1, item.getWidth());
		assertEquals(10000, item.getHeight());
		assertEquals(dimensions.minWidth, item.getThumbnailWidth());
		assertEquals(dimensions.maxHeight, item.getThumbnailHeight());
		assertEquals("image/jpeg", item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	@Test
	public void testWideError() throws Exception {
		AttachmentHeader h = new AttachmentHeader(msgId, "image/jpeg");
		InputStream is = getAssetInputStream("error_wide.jpg");
		Attachment a = new Attachment(is);
		AttachmentItem item = controller.getAttachmentItem(h, a, true);
		assertEquals(1920, item.getWidth());
		assertEquals(1, item.getHeight());
		assertEquals(dimensions.maxWidth, item.getThumbnailWidth());
		assertEquals(dimensions.minHeight, item.getThumbnailHeight());
		assertEquals("image/jpeg", item.getMimeType());
		assertEquals("jpg", item.getExtension());
		assertFalse(item.hasError());
	}

	private InputStream getUrlInputStream(String url) throws IOException {
		return new URL(url).openStream();
	}

	private InputStream getAssetInputStream(String name) throws IOException {
		AssetManager assets = InstrumentationRegistry.getContext().getAssets();
		return assets.open(name);
	}

	public static byte[] getRandomBytes(int length) {
		byte[] b = new byte[length];
		new Random().nextBytes(b);
		return b;
	}

	public static byte[] getRandomId() {
		return getRandomBytes(UniqueId.LENGTH);
	}

}
