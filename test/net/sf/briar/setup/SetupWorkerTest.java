package net.sf.briar.setup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipOutputStream;

import junit.framework.TestCase;
import net.sf.briar.TestUtils;
import net.sf.briar.api.i18n.I18n;
import net.sf.briar.api.setup.SetupCallback;
import net.sf.briar.api.setup.SetupParameters;
import net.sf.briar.util.ZipUtils;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SetupWorkerTest extends TestCase {

	private static final int HEADER_SIZE = 1234;

	private final File testDir = TestUtils.getTestDirectory();
	private final File jar = new File(testDir, "test.jar");

	@Before
	public void setUp() throws IOException {
		testDir.mkdirs();
		jar.createNewFile();
	}

	@Test
	public void testHaltsIfNotRunningFromJar() {
		Mockery context = new Mockery();
		final SetupCallback callback = context.mock(SetupCallback.class);
		SetupParameters params = context.mock(SetupParameters.class);
		I18n i18n = context.mock(I18n.class);
		context.checking(new Expectations() {{
			oneOf(callback).error("Not running from jar");
		}});

		new SetupWorker(callback, params, i18n, testDir).run();

		context.assertIsSatisfied();
		File[] children = testDir.listFiles();
		assertNotNull(children);
		assertEquals(1, children.length);
		assertEquals(jar, children[0]);
	}

	@Test
	public void testHaltsIfDestinationDoesNotExist() {
		final File nonExistent = new File(testDir, "does.not.exist");
		Mockery context = new Mockery();
		final SetupCallback callback = context.mock(SetupCallback.class);
		final SetupParameters params = context.mock(SetupParameters.class);
		I18n i18n = context.mock(I18n.class);
		context.checking(new Expectations() {{
			oneOf(params).getChosenLocation();
			will(returnValue(nonExistent));
			oneOf(callback).notFound(nonExistent);
		}});

		new SetupWorker(callback, params, i18n, jar).run();

		context.assertIsSatisfied();
		File[] children = testDir.listFiles();
		assertNotNull(children);
		assertEquals(1, children.length);
		assertEquals(jar, children[0]);
	}

	@Test
	public void testHaltsIfDestinationIsNotADirectory() {
		Mockery context = new Mockery();
		final SetupCallback callback = context.mock(SetupCallback.class);
		final SetupParameters params = context.mock(SetupParameters.class);
		I18n i18n = context.mock(I18n.class);
		context.checking(new Expectations() {{
			oneOf(params).getChosenLocation();
			will(returnValue(jar));
			oneOf(callback).notDirectory(jar);
		}});

		new SetupWorker(callback, params, i18n, jar).run();

		context.assertIsSatisfied();
		File[] children = testDir.listFiles();
		assertNotNull(children);
		assertEquals(1, children.length);
		assertEquals(jar, children[0]);
	}

	@Test
	public void testCreatesExpectedFiles() throws IOException {
		final File setupDat = new File(testDir, "Briar/Data/setup.dat");
		final File jreFoo = new File(testDir, "Briar/Data/jre/foo");
		final File fooJar = new File(testDir, "Briar/Data/foo.jar");
		final File fooTtf = new File(testDir, "Briar/Data/foo.ttf");
		final File fooXyz = new File(testDir, "Briar/Data/foo.xyz");
		createJar();

		Mockery context = new Mockery();
		final SetupCallback callback = context.mock(SetupCallback.class);
		final SetupParameters params = context.mock(SetupParameters.class);
		final I18n i18n = context.mock(I18n.class);
		context.checking(new Expectations() {{
			oneOf(params).getChosenLocation();
			will(returnValue(testDir));
			allowing(callback).isCancelled();
			will(returnValue(false));
			oneOf(callback).copyingFile(setupDat);
			oneOf(params).getExeHeaderSize();
			will(returnValue((long) HEADER_SIZE));
			oneOf(callback).extractingFile(jreFoo);
			oneOf(callback).extractingFile(fooJar);
			oneOf(callback).extractingFile(fooTtf);
			oneOf(i18n).saveLocale(new File(testDir, "Briar"));
			oneOf(callback).installed(new File(testDir, "Briar"));
		}});

		new SetupWorker(callback, params, i18n, jar).run();

		context.assertIsSatisfied();
		assertTrue(setupDat.exists());
		assertTrue(setupDat.isFile());
		assertEquals(jar.length(), setupDat.length());
		assertTrue(jreFoo.exists());
		assertTrue(jreFoo.isFile());
		assertEquals("one one one".length(), jreFoo.length());
		assertTrue(fooJar.exists());
		assertTrue(fooJar.isFile());
		assertEquals("two two two".length(), fooJar.length());
		assertTrue(fooTtf.exists());
		assertTrue(fooTtf.isFile());
		assertEquals("three three three".length(), fooTtf.length());
		assertFalse(fooXyz.exists());
		assertTrue(new File(testDir, "Briar/run-windows.vbs").exists());
		assertTrue(new File(testDir, "Briar/run-mac.command").exists());
		assertTrue(new File(testDir, "Briar/run-linux.sh").exists());
	}

	private void createJar() throws IOException {
		FileOutputStream out = new FileOutputStream(jar);
		byte[] header = new byte[HEADER_SIZE];
		out.write(header);
		ZipOutputStream zip = new ZipOutputStream(out);
		File temp = new File(testDir, "temp");
		TestUtils.createFile(temp, "one one one");
		ZipUtils.copyToZip("jre/foo", temp, zip);
		temp.delete();
		TestUtils.createFile(temp, "two two two");
		ZipUtils.copyToZip("foo.jar", temp, zip);
		temp.delete();
		TestUtils.createFile(temp, "three three three");
		ZipUtils.copyToZip("foo.ttf", temp, zip);
		temp.delete();
		TestUtils.createFile(temp, "four four four");
		ZipUtils.copyToZip("foo.xyz", temp, zip);
		temp.delete();
		zip.flush();
		zip.close();
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
