package org.briarproject.briar.android.attachment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;

import static java.util.Arrays.asList;
import static org.briarproject.bramble.api.nullsafety.NullSafety.requireNonNull;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class PngSuiteAttachmentCreationTaskTest
		extends AbstractAttachmentCreationTaskTest {

	@Parameters
	public static Iterable<String> data() throws IOException {
		return asList(requireNonNull(getAssetManager().list("PngSuite")));
	}

	private final String filename;

	public PngSuiteAttachmentCreationTaskTest(String filename) {
		this.filename = filename;
	}

	@Test
	public void testPngSuiteCompress() throws Exception {
		if (filename.startsWith("x")) {
			try {
				testCompress("PngSuite/" + filename, "image/png");
				fail();
			} catch (IOException expected) {
				// Expected
			}
		} else {
			testCompress("PngSuite/" + filename, "image/png");
		}
	}
}
