package org.briarproject.bramble.api.mailbox;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.briarproject.bramble.api.mailbox.MailboxConstants.API_CLIENT_TOO_OLD;
import static org.briarproject.bramble.api.mailbox.MailboxConstants.API_SERVER_TOO_OLD;
import static org.briarproject.bramble.api.mailbox.MailboxHelper.getHighestCommonMajorVersion;
import static org.junit.Assert.assertEquals;

public class MailboxHelperTest {

	private final Random random = new Random();

	@Test
	public void testGetHighestCommonMajorVersion() {
		assertEquals(2, getHighestCommonMajorVersion(v(2), v(2)));
		assertEquals(2, getHighestCommonMajorVersion(v(1, 2), v(2, 3, 4)));
		assertEquals(2, getHighestCommonMajorVersion(v(2, 3, 4), v(2)));
		assertEquals(2, getHighestCommonMajorVersion(v(2), v(2, 3, 4)));

		assertEquals(API_CLIENT_TOO_OLD,
				getHighestCommonMajorVersion(v(2), v(3, 4)));
		assertEquals(API_CLIENT_TOO_OLD,
				getHighestCommonMajorVersion(v(2), v(1, 3)));
		assertEquals(API_SERVER_TOO_OLD,
				getHighestCommonMajorVersion(v(3, 4, 5), v(2)));
		assertEquals(API_SERVER_TOO_OLD,
				getHighestCommonMajorVersion(v(1, 3), v(2)));
	}

	private List<MailboxVersion> v(int... ints) {
		List<MailboxVersion> versions = new ArrayList<>(ints.length);
		for (int v : ints) {
			// minor versions should not matter
			versions.add(new MailboxVersion(v, random.nextInt(42)));
		}
		return versions;
	}

}
