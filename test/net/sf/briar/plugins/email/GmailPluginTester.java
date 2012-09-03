package net.sf.briar.plugins.email;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Executors;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.plugins.simplex.SimplexPluginCallback;
import net.sf.briar.api.plugins.simplex.SimplexTransportReader;
import net.sf.briar.api.plugins.simplex.SimplexTransportWriter;

import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

/*
 * Uses environment variables USER_GMAIL_ADDRESS, GMAIL_USERNAME, GMAIL_PASSWORD,
 * and CONTACT1_EMAIL - (as recipient email address)
 */

public class GmailPluginTester {

SimplexPluginCallback callback;	
TransportProperties local, props1;
TransportConfig config;
ContactId test1;
Map<ContactId,TransportProperties> map = new HashMap<ContactId, TransportProperties>();
	
	@Before
	public void setup()
	{
		local = new TransportProperties();
		local.put("email",System.getenv("USER_GMAIL_ADDRESS"));
		
		config = new TransportConfig();
		config.put("username", System.getenv("GMAIL_USERNAME"));
		config.put("password", System.getenv("GMAIL_PASSWORD"));
		
		props1 = new TransportProperties();
		props1.put("email", System.getenv("CONTACT1_EMAIL"));
		test1 = new ContactId(12);
		map.put(test1, props1);
		assertEquals(1,map.size());
		
		callback = new SimplexPluginCallback() {
			
			public void showMessage(String... message) {
								
			}
			
			public boolean showConfirmationMessage(String... message) {
				return false;
			}
			
			public int showChoice(String[] options, String... message) {
				return 0;
			}
			
			public void setLocalProperties(TransportProperties p) {
				local = p;
			}
			
			public void setConfig(TransportConfig c) {
				config = c;
			}
						
			public Map<ContactId, TransportProperties> getRemoteProperties() {
				return map;
			}
			
			public TransportProperties getLocalProperties() {
				return local;
			}
			
			public TransportConfig getConfig() {
				return config;
			}
			
			public void writerCreated(ContactId c, SimplexTransportWriter w) {}
			
			public void readerCreated(SimplexTransportReader r) {}
		};
		
		callback.setLocalProperties(local);		
		callback.setConfig(config);
	}
	
	@Test
	public void testGetID()
	{
		GmailPlugin pluginTest = new GmailPlugin(Executors.newSingleThreadExecutor(), callback);
		assertArrayEquals(GmailPlugin.TRANSPORT_ID,pluginTest.getId().getBytes());
	}
	
	@Test
	public void testPluginFactoryCreation()
	{
		GmailPluginFactory plugin = new GmailPluginFactory();
		plugin.createPlugin(Executors.newSingleThreadExecutor(), callback);
	}
	
	@Test
	public void testGmailPluginIMAP()
	{
		GmailPlugin pluginTest = new GmailPlugin(Executors.newSingleThreadExecutor(), callback);
		try {
			pluginTest.start();
		} catch (IOException e) {
			System.out.println("IO Exception got caught");
		}
	}
	
	@Test
	public void testGmailSMTP()
	{
		GmailPlugin pluginTest = new GmailPlugin(Executors.newSingleThreadExecutor(), callback);
		assertEquals(true, pluginTest.connectSMTP(test1));
		assertEquals(false, pluginTest.connectSMTP(new ContactId(7)));
	}

}
