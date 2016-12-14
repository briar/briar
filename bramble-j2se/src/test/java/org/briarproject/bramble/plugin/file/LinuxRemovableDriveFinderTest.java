package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.test.BrambleTestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LinuxRemovableDriveFinderTest extends BrambleTestCase {

	@Test
	public void testParseMountPoint() {
		LinuxRemovableDriveFinder f = new LinuxRemovableDriveFinder();
		String line = "/dev/sda3 on / type ext3"
			+ " (rw,errors=remount-ro,commit=0)";
		assertEquals("/", f.parseMountPoint(line));
		line = "gvfs-fuse-daemon on /home/alice/.gvfs"
			+ " type fuse.gvfs-fuse-daemon (rw,nosuid,nodev,user=alice)";
		assertEquals(null, f.parseMountPoint(line)); // Can't be parsed
		line = "fusectl on /sys/fs/fuse/connections type fusectl (rw)";
		assertEquals(null, f.parseMountPoint(line)); // Can't be parsed
		line = "/dev/sdd1 on /media/HAZ SPACE(!) type vfat"
			+ " (rw,nosuid,nodev,uhelper=udisks,uid=1000,gid=1000,"
			+ "shortname=mixed,dmask=0077,utf8=1,showexec,flush)";
		assertEquals("/media/HAZ SPACE(!)", f.parseMountPoint(line));
	}
}
