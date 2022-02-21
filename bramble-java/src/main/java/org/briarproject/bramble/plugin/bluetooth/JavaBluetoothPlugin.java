package org.briarproject.bramble.plugin.bluetooth;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.Backoff;
import org.briarproject.bramble.api.plugin.PluginCallback;
import org.briarproject.bramble.api.plugin.duplex.DuplexTransportConnection;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.StringUtils.fromHexString;
import static org.briarproject.bramble.util.StringUtils.isValidMac;
import static org.briarproject.bramble.util.StringUtils.macToString;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class JavaBluetoothPlugin extends
		AbstractBluetoothPlugin<StreamConnection, StreamConnectionNotifier> {

	private static final Logger LOG =
			getLogger(JavaBluetoothPlugin.class.getName());

	// Non-null if the plugin started successfully
	private volatile LocalDevice localDevice = null;

	JavaBluetoothPlugin(BluetoothConnectionLimiter connectionManager,
			BluetoothConnectionFactory<StreamConnection> connectionFactory,
			Executor ioExecutor,
			Executor wakefulIoExecutor,
			SecureRandom secureRandom,
			Backoff backoff,
			PluginCallback callback,
			long maxLatency,
			int maxIdleTime) {
		super(connectionManager, connectionFactory, ioExecutor,
				wakefulIoExecutor, secureRandom, backoff, callback,
				maxLatency, maxIdleTime);
	}

	@Override
	void initialiseAdapter() throws IOException {
		try {
			localDevice = LocalDevice.getLocalDevice();
		} catch (UnsatisfiedLinkError | BluetoothStateException e) {
			throw new IOException(e);
		}
	}

	@Override
	boolean isAdapterEnabled() {
		return localDevice != null && LocalDevice.isPowerOn();
	}

	@Nullable
	@Override
	String getBluetoothAddress() {
		if (localDevice == null) return null;
		return macToString(fromHexString(localDevice.getBluetoothAddress()));
	}

	@Override
	StreamConnectionNotifier openServerSocket(String uuid) throws IOException {
		String url = makeServerSocketUrl(uuid);
		return (StreamConnectionNotifier) Connector.open(url);
	}

	@Override
	void tryToClose(@Nullable StreamConnectionNotifier ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	@Override
	DuplexTransportConnection acceptConnection(StreamConnectionNotifier ss)
			throws IOException {
		return connectionFactory.wrapSocket(this, ss.acceptAndOpen());
	}

	@Override
	boolean isValidAddress(String address) {
		return isValidMac(address);
	}

	@Override
	DuplexTransportConnection connectTo(String address, String uuid)
			throws IOException {
		throw new IOException("Not implemented"); // TODO
	}

	@Override
	@Nullable
	DuplexTransportConnection discoverAndConnect(String uuid) {
		return null; // TODO
	}

	@Override
	public void stopDiscoverAndConnect() {
		// TODO
	}

	private String makeServerSocketUrl(String uuid) {
		uuid = uuid.replaceAll("-", "");
		return "btspp://" + "localhost" + ":" + uuid
				+ ";encrypt=false;authenticate=false";
	}
}
