package org.briarproject.bramble.qrcode;

import org.briarproject.bramble.api.Pair;
import org.briarproject.bramble.api.keyagreement.KeyAgreementConstants;
import org.briarproject.bramble.api.mailbox.MailboxConstants;
import org.briarproject.bramble.api.qrcode.QrCodeClassifier;
import org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType;
import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType.BQP;
import static org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType.MAILBOX;
import static org.briarproject.bramble.api.qrcode.QrCodeClassifier.QrCodeType.UNKNOWN;
import static org.briarproject.bramble.test.TestUtils.getRandomBytes;
import static org.briarproject.bramble.util.StringUtils.ISO_8859_1;
import static org.junit.Assert.assertEquals;

public class QrCodeClassifierImplTest extends BrambleTestCase {

	private final QrCodeClassifier classifier = new QrCodeClassifierImpl();

	@Test
	public void testClassifiesEmptyStringAsUnknown() {
		Pair<QrCodeType, Integer> result = classifier.classifyQrCode("");
		assertEquals(UNKNOWN, result.getFirst());
		assertEquals(0, result.getSecond().intValue());
	}

	@Test
	public void testClassifiesKeyAgreement() {
		byte[] payloadBytes = getRandomBytes(123);
		for (int version = 0; version < 32; version++) {
			int typeAndVersion =
					(KeyAgreementConstants.QR_FORMAT_ID << 5) | version;
			payloadBytes[0] = (byte) typeAndVersion;
			String payload = new String(payloadBytes, ISO_8859_1);
			Pair<QrCodeType, Integer> result =
					classifier.classifyQrCode(payload);
			assertEquals(BQP, result.getFirst());
			assertEquals(version, result.getSecond().intValue());
		}
	}

	@Test
	public void testClassifiesMailbox() {
		byte[] payloadBytes = getRandomBytes(123);
		for (int version = 0; version < 32; version++) {
			int typeAndVersion =
					(MailboxConstants.QR_FORMAT_ID << 5) | version;
			payloadBytes[0] = (byte) typeAndVersion;
			String payload = new String(payloadBytes, ISO_8859_1);
			Pair<QrCodeType, Integer> result =
					classifier.classifyQrCode(payload);
			assertEquals(MAILBOX, result.getFirst());
			assertEquals(version, result.getSecond().intValue());
		}
	}

	@Test
	public void testClassifiesUnknownFormatIdAsUnknown() {
		byte[] payloadBytes = getRandomBytes(123);
		int unknownFormatId = MailboxConstants.QR_FORMAT_ID + 1;
		int typeAndVersion = unknownFormatId << 5;
		payloadBytes[0] = (byte) typeAndVersion;
		String payload = new String(payloadBytes, ISO_8859_1);
		Pair<QrCodeType, Integer> result = classifier.classifyQrCode(payload);
		assertEquals(UNKNOWN, result.getFirst());
		assertEquals(0, result.getSecond().intValue());
	}
}
