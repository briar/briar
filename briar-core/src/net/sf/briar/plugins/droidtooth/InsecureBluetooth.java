package net.sf.briar.plugins.droidtooth;

import static java.util.logging.Level.INFO;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;

// Based on http://stanford.edu/~tpurtell/InsecureBluetooth.java by T.J. Purtell
class InsecureBluetooth {

	private static final Logger LOG =
			Logger.getLogger(InsecureBluetooth.class.getName());

	private static final int TYPE_RFCOMM = 1;

	@SuppressLint("NewApi")
	static BluetoothServerSocket listen(BluetoothAdapter adapter, String name,
			UUID uuid) throws IOException {
		if(Build.VERSION.SDK_INT >= 10) {
			if(LOG.isLoggable(INFO)) LOG.info("Listening with new API");
			return adapter.listenUsingInsecureRfcommWithServiceRecord(name,
					uuid);
		}
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Listening via reflection");
			// Find an available channel
			String className = BluetoothAdapter.class.getCanonicalName()
					+ ".RfcommChannelPicker";
			Class<?> channelPickerClass = null;
			Class<?>[] children = BluetoothAdapter.class.getDeclaredClasses();
			for(Class<?> c : children) {
				if(c.getCanonicalName().equals(className)) {
					channelPickerClass = c;
					break;
				}
			}
			if(channelPickerClass == null)
				throw new IOException("Can't find channel picker class");
			Constructor<?> constructor =
					channelPickerClass.getDeclaredConstructor(UUID.class);
			constructor.setAccessible(true);
			Object channelPicker = constructor.newInstance(uuid);
			Method nextChannel =
					channelPickerClass.getDeclaredMethod("nextChannel");
			nextChannel.setAccessible(true);
			int channel = (Integer) nextChannel.invoke(channelPicker);
			if(channel == -1) throw new IOException("No available channels");
			// Listen on the channel
			BluetoothServerSocket socket = listen(channel);
			// Add a service record
			Field f = BluetoothAdapter.class.getDeclaredField("mService");
			f.setAccessible(true);
			Object mService = f.get(adapter);
			Method addRfcommServiceRecord =
					mService.getClass().getDeclaredMethod(
							"addRfcommServiceRecord", String.class,
							ParcelUuid.class, int.class, IBinder.class);
			addRfcommServiceRecord.setAccessible(true);
			int handle = (Integer) addRfcommServiceRecord.invoke(mService, name,
					new ParcelUuid(uuid), channel, new Binder());
			if(handle == -1) {
				socket.close();
				throw new IOException("Can't register SDP record for " + name);
			}
			Field f1 = BluetoothAdapter.class.getDeclaredField("mHandler");
			f1.setAccessible(true);
			Object mHandler = f1.get(adapter);
			Method setCloseHandler = socket.getClass().getDeclaredMethod(
					"setCloseHandler", Handler.class, int.class);
			setCloseHandler.setAccessible(true);
			setCloseHandler.invoke(socket, mHandler, handle);
			return socket;
		} catch(NoSuchMethodException e) {
			throw new IOException(e.toString());
		} catch(NoSuchFieldException e) {
			throw new IOException(e.toString());
		} catch(IllegalAccessException e) {
			throw new IOException(e.toString());
		} catch(InstantiationException e) {
			throw new IOException(e.toString());
		} catch(InvocationTargetException e) {
			if(e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			} else {
				throw new IOException(e.toString());
			}
		}
	}

	private static BluetoothServerSocket listen(int port) throws IOException {
		try {
			Constructor<BluetoothServerSocket> constructor =
					BluetoothServerSocket.class.getDeclaredConstructor(
							int.class, boolean.class, boolean.class, int.class);
			constructor.setAccessible(true);
			BluetoothServerSocket socket = constructor.newInstance(TYPE_RFCOMM,
					false, false, port);
			Field f = BluetoothServerSocket.class.getDeclaredField("mSocket");
			f.setAccessible(true);
			Object mSocket = f.get(socket);
			Method bindListen =
					mSocket.getClass().getDeclaredMethod("bindListen");
			bindListen.setAccessible(true);
			int errno = (Integer) bindListen.invoke(mSocket);
			if(errno != 0) {
				socket.close();
				throw new IOException("Can't bind: errno " + errno);
			}
			return socket;
		} catch(NoSuchMethodException e) {
			throw new IOException(e.toString());
		} catch(NoSuchFieldException e) {
			throw new IOException(e.toString());
		} catch(IllegalAccessException e) {
			throw new IOException(e.toString());
		} catch(InstantiationException e) {
			throw new IOException(e.toString());
		} catch(InvocationTargetException e) {
			if(e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			} else {
				throw new IOException(e.toString());
			}
		}
	}

	@SuppressLint("NewApi")
	static BluetoothSocket createSocket(BluetoothDevice device, UUID uuid)
			throws IOException {
		if(Build.VERSION.SDK_INT >= 10) {
			if(LOG.isLoggable(INFO)) LOG.info("Creating socket with new API");
			return device.createInsecureRfcommSocketToServiceRecord(uuid);
		}
		try {
			if(LOG.isLoggable(INFO)) LOG.info("Creating socket via reflection");
			Constructor<BluetoothSocket> constructor =
					BluetoothSocket.class.getDeclaredConstructor(int.class,
							int.class, boolean.class, boolean.class,
							BluetoothDevice.class, int.class, ParcelUuid.class);
			constructor.setAccessible(true);
			return constructor.newInstance(TYPE_RFCOMM, -1, false, true, device,
					-1, new ParcelUuid(uuid));
		} catch(NoSuchMethodException e) {
			throw new IOException(e.toString());
		} catch(IllegalAccessException e) {
			throw new IOException(e.toString());
		} catch(InstantiationException e) {
			throw new IOException(e.toString());
		} catch(InvocationTargetException e) {
			if(e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			} else {
				throw new IOException(e.toString());
			}
		}
	}
}
