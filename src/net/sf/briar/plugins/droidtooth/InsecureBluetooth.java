package net.sf.briar.plugins.droidtooth;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

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

	@SuppressLint("NewApi")
	static BluetoothServerSocket listen(BluetoothAdapter adapter, String name,
			UUID uuid) throws IOException {
		if(Build.VERSION.SDK_INT >= 10) {
			return adapter.listenUsingInsecureRfcommWithServiceRecord(name,
					uuid);
		}
		try {
			String className = BluetoothAdapter.class.getName()
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
			if(constructor == null)
				throw new IOException("Can't find channel picker constructor");
			Object channelPicker = constructor.newInstance(uuid);
			Method nextChannel = channelPickerClass.getDeclaredMethod(
					"nextChannel", new Class[0]);
			nextChannel.setAccessible(true);
			BluetoothServerSocket socket = null;
			int channel = (Integer) nextChannel.invoke(channelPicker,
					new Object[0]);
			if(channel == -1) throw new IOException("No available channels");
			socket = listen(channel);
			Field f = adapter.getClass().getDeclaredField("mService");
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
				try {
					socket.close();
				} catch(IOException ignored) {}
				throw new IOException("Can't register SDP record for " + name);
			}
			Field f1 = adapter.getClass().getDeclaredField("mHandler");
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
		BluetoothServerSocket socket = null;
		try {
			Constructor<BluetoothServerSocket> constructor =
					BluetoothServerSocket.class.getDeclaredConstructor(
							int.class, boolean.class, boolean.class, int.class);
			if(constructor == null)
				throw new IOException("Can't find server socket constructor");
			constructor.setAccessible(true);
			Field f = BluetoothSocket.class.getDeclaredField("TYPE_RFCOMM");
			f.setAccessible(true);
			int rfcommType = (Integer) f.get(null);
			socket = constructor.newInstance(rfcommType, false, false, port);
			Field f1 = socket.getClass().getDeclaredField("mSocket");
			f1.setAccessible(true);
			Object mSocket = f1.get(socket);
			Method bindListen = mSocket.getClass().getDeclaredMethod(
					"bindListen", new Class[0]);
			bindListen.setAccessible(true);
			Object result = bindListen.invoke(mSocket, new Object[0]);
			int errno = (Integer) result;
			if(errno != 0) {
				try {
					socket.close();
				} catch(IOException ignored) {}
				Method throwErrnoNative = mSocket.getClass().getMethod(
						"throwErrnoNative", int.class);
				throwErrnoNative.invoke(mSocket, errno);
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
			return device.createInsecureRfcommSocketToServiceRecord(uuid);
		}
		try {
			BluetoothSocket socket = null;
			Constructor<BluetoothSocket> constructor =
					BluetoothSocket.class.getDeclaredConstructor(int.class,
							int.class, boolean.class, boolean.class,
							BluetoothDevice.class, int.class, ParcelUuid.class);
			if(constructor == null)
				throw new IOException("Can't find socket constructor");
			constructor.setAccessible(true);
			Field f = BluetoothSocket.class.getDeclaredField("TYPE_RFCOMM");
			f.setAccessible(true);
			int typeRfcomm = (Integer) f.get(null);
			socket = constructor.newInstance(typeRfcomm, -1, false, true,
					device, -1, uuid != null ? new ParcelUuid(uuid) : null);
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
}
