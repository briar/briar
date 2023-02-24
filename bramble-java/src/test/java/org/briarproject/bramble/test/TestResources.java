package org.briarproject.bramble.test;

import org.briarproject.bramble.util.OsUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;

public class TestResources {

	@Before
	public void isRunningOnLinux() {
		assumeTrue(OsUtils.isLinux());
	}

	@Test
	public void canReadTorLinux() {
		InputStream input = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("x86_64/tor");
		assertNotNull(input);
	}

	@Test
	public void canReadObfs4ProxyLinux() {
		InputStream input = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("x86_64/obfs4proxy");
		assertNotNull(input);
	}

	@Test
	public void canReadSnowflakeLinux() {
		InputStream input = Thread.currentThread().getContextClassLoader()
				.getResourceAsStream("x86_64/snowflake");
		assertNotNull(input);
	}

}
