package net.sf.briar.transport;

import static org.junit.Assert.assertArrayEquals;
import net.sf.briar.BriarTestCase;
import net.sf.briar.api.transport.Segment;

import org.junit.Test;

public class XorErasureEncoderTest extends BriarTestCase {

	@Test
	public void testEncoding() {
		// Create a 100-byte frame
		Frame f = new Frame();
		f.setLength(100);
		byte[] b = f.getBuffer();
		for(int i = 0; i < 100; i++) b[i] = (byte) i;
		// Encode the frame
		XorErasureEncoder e = new XorErasureEncoder(4);
		Segment[] set = e.encodeFrame(f);
		// There should be four pieces of 34 bytes each
		assertEquals(4, set.length);
		for(int i = 0; i < 4; i++) assertEquals(34, set[i].getLength());
		// The first three pieces should contain the data, plus two zero bytes
		byte[] b1 = set[0].getBuffer();
		for(int i = 0; i < 34; i++) assertEquals(i, b1[i]);
		byte[] b2 = set[1].getBuffer();
		for(int i = 0; i < 34; i++) assertEquals(i + 34, b2[i]);
		byte[] b3 = set[2].getBuffer();
		for(int i = 0; i < 32; i++) assertEquals(i + 68, b3[i]);
		assertEquals(0, b3[32]);
		assertEquals(0, b3[33]);
		// The fourth piece should be the XOR of the other three
		byte[] b4 = set[3].getBuffer();
		byte[] expected = new byte[34];
		for(int i = 0; i < 34; i++) {
			expected[i] = (byte) (b1[i] ^ b2[i] ^ b3[i]);
		}
		assertArrayEquals(expected, b4);
	}
}
