package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import java.io.IOException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.api.plugins.Segment;
import net.sf.briar.api.plugins.SegmentSource;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class SegmentedConnectionDecrypterTest extends BriarTestCase {

	private static final int MAC_LENGTH = 32;

	private final Cipher frameCipher;
	private final ErasableKey frameKey;

	public SegmentedConnectionDecrypterTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
		frameKey = crypto.generateTestKey();
	}

	@Test
	public void testDecryption() throws Exception {
		// Calculate the ciphertext for the first frame
		byte[] plaintext = new byte[FRAME_HEADER_LENGTH + 123 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext, 0L, 123, 0);
		byte[] iv = IvEncoder.encodeIv(0L, frameCipher.getBlockSize());
		IvParameterSpec ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext = frameCipher.doFinal(plaintext, 0, plaintext.length);
		// Calculate the ciphertext for the second frame
		byte[] plaintext1 = new byte[FRAME_HEADER_LENGTH + 1234 + MAC_LENGTH];
		HeaderEncoder.encodeHeader(plaintext1, 1L, 1234, 0);
		IvEncoder.updateIv(iv, 1L);
		ivSpec = new IvParameterSpec(iv);
		frameCipher.init(Cipher.ENCRYPT_MODE, frameKey, ivSpec);
		byte[] ciphertext1 = frameCipher.doFinal(plaintext1, 0,
				plaintext1.length);
		// Use a connection decrypter to decrypt the ciphertext
		byte[][] frames = new byte[][] { ciphertext, ciphertext1 };
		SegmentSource in = new ByteArraySegmentSource(frames);
		FrameSource decrypter = new SegmentedConnectionDecrypter(in,
				frameCipher, frameKey, MAC_LENGTH);
		// First frame
		byte[] decrypted = new byte[MAX_FRAME_LENGTH];
		assertEquals(plaintext.length, decrypter.readFrame(decrypted));
		for(int i = 0; i < plaintext.length; i++) {
			assertEquals(plaintext[i], decrypted[i]);
		}
		// Second frame
		assertEquals(plaintext1.length, decrypter.readFrame(decrypted));
		for(int i = 0; i < plaintext1.length; i++) {
			assertEquals(plaintext1[i], decrypted[i]);
		}
	}

	private static class ByteArraySegmentSource implements SegmentSource {

		private final byte[][] frames;

		private int frame = 0;

		private ByteArraySegmentSource(byte[][] frames) {
			this.frames = frames;
		}

		public boolean readSegment(Segment s) throws IOException {
			if(frame == frames.length) return false;
			byte[] src = frames[frame];
			System.arraycopy(src, 0, s.getBuffer(), 0, src.length);
			s.setLength(src.length);
			s.setTransmissionNumber(frame);
			frame++;
			return true;
		}
	}
}
