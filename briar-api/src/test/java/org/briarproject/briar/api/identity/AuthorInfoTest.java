package org.briarproject.briar.api.identity;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.briarproject.briar.api.identity.AuthorInfo.Status.NONE;
import static org.briarproject.briar.api.identity.AuthorInfo.Status.VERIFIED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class AuthorInfoTest extends BrambleTestCase {

	@Test
	public void testEquals() {
		assertEquals(
				new AuthorInfo(NONE),
				new AuthorInfo(NONE, null)
		);
		assertEquals(
				new AuthorInfo(NONE, "test"),
				new AuthorInfo(NONE, "test")
		);

		assertNotEquals(
				new AuthorInfo(NONE),
				new AuthorInfo(VERIFIED)
		);
		assertNotEquals(
				new AuthorInfo(NONE, "test"),
				new AuthorInfo(NONE)
		);
		assertNotEquals(
				new AuthorInfo(NONE),
				new AuthorInfo(NONE, "test")
		);
		assertNotEquals(
				new AuthorInfo(NONE, "a"),
				new AuthorInfo(NONE, "b")
		);
	}

}
