package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MacRemovableDriveFinderTest extends BrambleTestCase {

	@Test
	public void testParseMountPoint() {
		MacRemovableDriveFinder f = new MacRemovableDriveFinder();
		String line = "/dev/disk0s3 on / (local, journaled)";
		assertEquals("/", f.parseMountPoint(line));
		line = "devfs on /dev (local)";
		assertEquals(null, f.parseMountPoint(line)); // Can't be parsed
		line = "<volfs> on /.vol";
		assertEquals(null, f.parseMountPoint(line)); // Can't be parsed
		line = "automount -nsl [117] on /Network (automounted)";
		assertEquals(null, f.parseMountPoint(line)); // Can't be parsed
		line = "/dev/disk1s1 on /Volumes/HAZ SPACE(!) (local, nodev, nosuid)";
		assertEquals("/Volumes/HAZ SPACE(!)", f.parseMountPoint(line));
	}
}
