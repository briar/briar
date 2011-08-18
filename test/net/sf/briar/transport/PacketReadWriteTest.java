package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.TAG_LENGTH;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.transport.PacketReader;
import net.sf.briar.api.transport.PacketWriter;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class PacketReadWriteTest extends TestCase {

	private final CryptoComponent crypto;
	private final Cipher tagCipher, packetCipher;
	private final SecretKey macKey, tagKey, packetKey;
	private final Mac mac;
	private final Random random;
	private final byte[] secret = new byte[100];
	private final int transportId = 999;
	private final long connection = 1234L;

	public PacketReadWriteTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		packetCipher = crypto.getPacketCipher();
		// Since we're sending packets to ourselves, we only need outgoing keys
		macKey = crypto.deriveOutgoingMacKey(secret);
		tagKey = crypto.deriveOutgoingTagKey(secret);
		packetKey = crypto.deriveOutgoingPacketKey(secret);
		mac = crypto.getMac();
		random = new Random();
	}

	@Test
	public void testWriteAndRead() throws Exception {
		// Generate two random packets
		byte[] packet = new byte[12345];
		random.nextBytes(packet);
		byte[] packet1 = new byte[321];
		random.nextBytes(packet1);
		// Write the packets
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter encrypter = new PacketEncrypterImpl(out, tagCipher,
				packetCipher, tagKey, packetKey);
		mac.init(macKey);
		PacketWriter writer = new PacketWriterImpl(encrypter, mac, transportId,
				connection);
		OutputStream out1 = writer.getOutputStream();
		out1.write(packet);
		writer.finishPacket();
		out1.write(packet1);
		writer.finishPacket();
		// Read the packets back
		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		byte[] firstTag = new byte[TAG_LENGTH];
		assertEquals(TAG_LENGTH, in.read(firstTag));
		PacketDecrypter decrypter = new PacketDecrypterImpl(firstTag, in,
				tagCipher, packetCipher, tagKey, packetKey);
		PacketReader reader = new PacketReaderImpl(decrypter, mac, transportId,
				connection);
		InputStream in1 = reader.getInputStream();
		byte[] recovered = new byte[packet.length];
		int offset = 0;
		while(offset < recovered.length) {
			int read = in1.read(recovered, offset, recovered.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered.length, offset);
		reader.finishPacket();
		assertTrue(Arrays.equals(packet, recovered));
		byte[] recovered1 = new byte[packet1.length];
		offset = 0;
		while(offset < recovered1.length) {
			int read = in1.read(recovered1, offset, recovered1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(recovered1.length, offset);
		reader.finishPacket();
		assertTrue(Arrays.equals(packet1, recovered1));
	}
}
