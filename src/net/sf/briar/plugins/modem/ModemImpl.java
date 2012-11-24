package net.sf.briar.plugins.modem;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static jssc.SerialPort.PURGE_RXCLEAR;
import static jssc.SerialPort.PURGE_TXCLEAR;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

class ModemImpl implements Modem, SerialPortEventListener {

	private static final Logger LOG =
			Logger.getLogger(ModemImpl.class.getName());
	private static final int MAX_LINE_LENGTH = 256;
	private static final int[] BAUD_RATES = {
		256000, 128000, 115200, 57600, 38400, 19200, 14400, 9600, 4800, 1200
	};
	private static final int OK_TIMEOUT = 5 * 1000; // Milliseconds
	private static final int CONNECT_TIMEOUT = 60 * 1000; // Milliseconds

	private final Executor executor;
	private final Callback callback;
	private final SerialPort port;
	private final AtomicBoolean initialised, connected;
	private final Semaphore offHook;
	private final BlockingQueue<byte[]> received;
	private final byte[] line;

	private int lineLen = 0;


	ModemImpl(Executor executor, Callback callback, String portName) {
		this.executor = executor;
		this.callback = callback;
		port = new SerialPort(portName);
		initialised = new AtomicBoolean(false);
		offHook = new Semaphore(1);
		connected = new AtomicBoolean(false);
		received = new LinkedBlockingQueue<byte[]>();
		line = new byte[MAX_LINE_LENGTH];
	}

	public void init() throws IOException {
		if(LOG.isLoggable(INFO)) LOG.info("Initialising");
		try {
			// Open the serial port
			if(!port.openPort())
				throw new IOException("Failed to open serial port");
			// Find a suitable baud rate
			boolean foundBaudRate = false;
			for(int baudRate : BAUD_RATES) {
				if(port.setParams(baudRate, 8, 1, 0)) {
					foundBaudRate = true;
					break;
				}
			}
			if(!foundBaudRate)
				throw new IOException("Could not find a suitable baud rate");
			// Listen for incoming data and hangup events
			port.addEventListener(this);
			// Initialise the modem
			port.purgePort(PURGE_RXCLEAR | PURGE_TXCLEAR);
			port.writeBytes("ATZ\r\n".getBytes("US-ASCII")); // Reset
			port.writeBytes("ATE0\r\n".getBytes("US-ASCII")); // Echo off
		} catch(SerialPortException e) {
			tryToClose(port);
			throw new IOException(e.toString());
		}
		try {
			// Wait for the modem to respond "OK"
			synchronized(initialised) {
				if(!initialised.get()) initialised.wait(OK_TIMEOUT);
				if(!initialised.get())
					throw new IOException("Modem did not respond");
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while initialising modem");
			tryToClose(port);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while initialising modem");
		}
	}

	public boolean dial(String number) throws IOException {
		if(!offHook.tryAcquire()) {
			if(LOG.isLoggable(INFO))
				LOG.info("Not dialling - call in progress");
			return false;
		}
		if(LOG.isLoggable(INFO)) LOG.info("Dialling");
		try {
			port.writeBytes(("ATDT" + number + "\r\n").getBytes("US-ASCII"));
		} catch(SerialPortException e) {
			tryToClose(port);
			throw new IOException(e.toString());
		}
		try {
			synchronized(connected) {
				if(!connected.get()) connected.wait(CONNECT_TIMEOUT);
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while connecting outgoing call");
			tryToClose(port);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while connecting outgoing call");
		}
		if(connected.get()) return true;
		hangUp();
		return false;
	}

	public InputStream getInputStream() {
		return new ModemInputStream();
	}

	public OutputStream getOutputStream() {
		return new ModemOutputStream();
	}

	public void hangUp() throws IOException {
		if(LOG.isLoggable(INFO)) LOG.info("Hanging up");
		try {
			port.setDTR(false);
		} catch(SerialPortException e) {
			tryToClose(port);
			throw new IOException(e.toString());
		}
		received.add(new byte[0]); // Empty buffer indicates EOF
		connected.set(false);
		offHook.release();
	}

	public void serialEvent(SerialPortEvent ev) {
		try {
			if(ev.isRXCHAR()) {
				byte[] b = port.readBytes();
				if(connected.get()) received.add(b);
				else handleText(b);
			} else if(ev.isDSR() && ev.getEventValue() == 0) {
				if(LOG.isLoggable(INFO)) LOG.info("Remote end hung up");
				hangUp();
			}
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		} catch(SerialPortException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	private void handleText(byte[] b) throws IOException {
		if(lineLen + b.length > MAX_LINE_LENGTH) {
			tryToClose(port);
			throw new IOException("Line too long");
		}
		for(int i = 0; i < b.length; i++) {
			line[lineLen] = b[i];
			if(b[i] == '\n') {
				String s = new String(line, 0, lineLen, "US-ASCII").trim();
				lineLen = 0;
				if(LOG.isLoggable(INFO)) LOG.info("Modem status: " + s);
				if(s.startsWith("CONNECT")) {
					// There might be data in the buffer as well as text
					int off = i + 1;
					if(off < b.length) {
						byte[] data = new byte[b.length - off];
						System.arraycopy(b, off, data, 0, data.length);
						received.add(data);
					}
					synchronized(connected) {
						if(!connected.getAndSet(true))
							connected.notifyAll();
					}
					return;
				} else if(s.equals("OK")) {
					synchronized(initialised) {
						if(!initialised.getAndSet(true))
							initialised.notifyAll();
					}
				} else if(s.equals("RING")) {
					executor.execute(new Runnable() {
						public void run() {
							try {
								answer();
							} catch(IOException e) {
								if(LOG.isLoggable(WARNING))
									LOG.warning(e.toString());
							}
						}
					});
				}
			} else {
				lineLen++;
			}
		}
	}

	private void answer() throws IOException {
		if(offHook.tryAcquire()) {
			if(LOG.isLoggable(INFO))
				LOG.info("Not answering - call in progress");
			return;
		}
		if(LOG.isLoggable(INFO)) LOG.info("Answering");
		try {
			port.writeBytes("ATA\r\n".getBytes("US-ASCII"));
		} catch(SerialPortException e) {
			tryToClose(port);
			throw new IOException(e.toString());
		}
		try {
			synchronized(connected) {
				if(!connected.get()) connected.wait(CONNECT_TIMEOUT);
			}
		} catch(InterruptedException e) {
			if(LOG.isLoggable(WARNING))
				LOG.warning("Interrupted while connecting incoming call");
			tryToClose(port);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while connecting incoming call");
		}
		if(connected.get()) callback.incomingCallConnected();
		else hangUp();
	}

	private void tryToClose(SerialPort port) {
		try {
			port.closePort();
		} catch(SerialPortException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}

	private class ModemInputStream extends InputStream {

		private byte[] buf = null;
		private int offset = 0;

		@Override
		public int read() throws IOException {
			getBufferIfNecessary();
			if(buf.length == 0) return -1;
			return buf[offset++];
		}

		@Override
		public int read(byte[] b) throws IOException {
			getBufferIfNecessary();
			if(buf.length == 0) return -1;
			int len = Math.min(b.length, buf.length - offset);
			System.arraycopy(buf, offset, b, 0, len);
			offset += len;
			return len;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			getBufferIfNecessary();
			if(buf.length == 0) return -1;
			len = Math.min(len, buf.length - offset);
			System.arraycopy(buf, offset, b, off, len);
			offset += len;
			return len;
		}

		private void getBufferIfNecessary() throws IOException {
			if(buf == null || offset == buf.length) {
				try {
					buf = received.take();
				} catch(InterruptedException e) {
					if(LOG.isLoggable(WARNING))
						LOG.warning("Interrupted while reading");
					tryToClose(port);
					Thread.currentThread().interrupt();
					throw new IOException(e.toString());
				}
				offset = 0;
			}
		}
	}

	private class ModemOutputStream extends OutputStream {

		@Override
		public void write(int b) throws IOException {
			try {
				port.writeByte((byte) b);
			} catch(SerialPortException e) {
				tryToClose(port);
				throw new IOException(e.toString());
			}
		}

		@Override
		public void write(byte[] b) throws IOException {
			try {
				port.writeBytes(b);
			} catch(SerialPortException e) {
				tryToClose(port);
				throw new IOException(e.toString());
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if(len < b.length) {
				byte[] copy = new byte[len];
				System.arraycopy(b, off, copy, 0, len);
				write(copy);
			} else {
				write(b);
			}
		}
	}
}
