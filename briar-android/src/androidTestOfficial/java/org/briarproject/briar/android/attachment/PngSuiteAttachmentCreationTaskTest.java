package org.briarproject.briar.android.attachment;

import org.briarproject.bramble.api.Pair;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class PngSuiteAttachmentCreationTaskTest
		extends AbstractAttachmentCreationTaskTest {

	@Parameters
	public static Iterable<Pair<String, Boolean>> data() throws IOException {
		List<Pair<String, Boolean>> data = new ArrayList<>();
		for (String file : getAssetFiles("PngSuite")) {
			if (file.endsWith(".png")) {
				boolean shouldPass = !file.startsWith("x");
				data.add(new Pair<>("PngSuite/" + file, shouldPass));
			}
		}
		return data;
	}

	private final String filename;
	private final boolean shouldPass;

	public PngSuiteAttachmentCreationTaskTest(Pair<String, Boolean> data) {
		filename = data.getFirst();
		shouldPass = data.getSecond();
	}

	@Test
	public void testPngSuiteCompress() throws Exception {
		if (shouldPass) {
			testCompress(filename, "image/png");
		} else {
			try {
				testCompress(filename, "image/png");
				fail();
			} catch (IOException expected) {
				// Expected
			}
		}
	}
}
