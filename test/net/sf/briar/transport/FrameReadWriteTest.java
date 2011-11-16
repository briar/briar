package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.IV_LENGTH;
import static org.junit.Assert.assertArrayEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import net.sf.briar.api.crypto.ErasableKey;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.protocol.TransportIndex;
import net.sf.briar.api.transport.ConnectionReader;
import net.sf.briar.api.transport.ConnectionWriter;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class FrameReadWriteTest extends TestCase {

	private final CryptoComponent crypto;
	private final Cipher ivCipher, frameCipher;
	private final Random random;
	private final byte[] outSecret;
	private final ErasableKey ivKey, frameKey, macKey;
	private final Mac mac;
	private final TransportIndex transportIndex = new TransportIndex(13);
	private final long connection = 12345L;

	public FrameReadWriteTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		ivCipher = crypto.getIvCipher();
		frameCipher = crypto.getFrameCipher();
		random = new Random();
		// Since we're sending frames to ourselves, we only need outgoing keys
		outSecret = new byte[32];
		random.nextBytes(outSecret);
		ivKey = crypto.deriveIvKey(outSecret, true);
		frameKey = crypto.deriveFrameKey(outSecret, true);
		macKey = crypto.deriveMacKey(outSecret, true);
		mac = crypto.getMac();
	}

	@Test
	public void testInitiatorWriteAndRead() throws Exception {
		testWriteAndRead(true);
	}

	@Test
	public void testResponderWriteAndRead() throws Exception {
		testWriteAndRead(false);
	}

	private void testWriteAndRead(boolean initiator) throws Exception {
		// Create and encrypt the IV
		byte[] iv = IvEncoder.encodeIv(initiator, transportIndex.getInt(),
				connection);
		ivCipher.init(Cipher.ENCRYPT_MODE, ivKey);
		byte[] encryptedIv = ivCipher.doFinal(iv);
		assertEquals(IV_LENGTH, encryptedIv.length);
		// Generate two random frames
		byte[] frame = new byte[12345];
		random.nextBytes(frame);
		byte[] frame1 = new byte[321];
		random.nextBytes(frame1);
		// Copy the keys - the copies will be erased
		ErasableKey frameCopy = frameKey.copy();
		ErasableKey ivCopy = ivKey.copy();
		ErasableKey macCopy = macKey.copy();
		// Write the frames
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ConnectionEncrypter encrypter = new ConnectionEncrypterImpl(out,
				Long.MAX_VALUE, iv, ivCipher, frameCipher, ivCopy, frameCopy);
		ConnectionWriter writer = new ConnectionWriterImpl(encrypter, mac,
				macCopy);
		OutputStream out1 = writer.getOutputStream();
		out1.write(frame);
		out1.flush();
		out1.write(frame1);
		out1.flush();
		// Read the IV back
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		byte[] recoveredEncryptedIv = new byte[IV_LENGTH];
		assertEquals(IV_LENGTH, in.read(recoveredEncryptedIv));
		assertArrayEquals(encryptedIv, recoveredEncryptedIv);
		// Decrypt the IV
		ivCipher.init(Cipher.DECRYPT_MODE, ivKey);
		byte[] recoveredIv = ivCipher.doFinal(recoveredEncryptedIv);
		iv = IvEncoder.encodeIv(initiator, transportIndex.getInt(), connection);
		assertArrayEquals(iv, recoveredIv);
		// Read the frames back
		ConnectionDecrypter decrypter = new ConnectionDecrypterImpl(in, iv,
				frameCipher, frameKey);
		ConnectionReader reader = new ConnectionReaderImpl(decrypter, mac,
				macKey);
		InputStream in1 = reader.getInputStream();
		byte[] recovered = new byte[frame.length];
		int offset = 0;
		while(offset < recovered.length) {
			int read = in1.read(recovered, offset, recovered.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered.length, offset);
		assertArrayEquals(frame, recovered);
		byte[] recovered1 = new byte[frame1.length];
		offset = 0;
		while(offset < recovered1.length) {
			int read = in1.read(recovered1, offset, recovered1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered1.length, offset);
		assertArrayEquals(frame1, recovered1);
	}
}
