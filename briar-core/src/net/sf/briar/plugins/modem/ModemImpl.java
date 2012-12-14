package net.sf.briar.plugins.modem;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static jssc.SerialPort.PURGE_RXCLEAR;
import static jssc.SerialPort.PURGE_TXCLEAR;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.logging.Logger;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

class ModemImpl implements Modem, WriteHandler, SerialPortEventListener {

	private static final Logger LOG =
			Logger.getLogger(ModemImpl.class.getName());
	private static final int MAX_LINE_LENGTH = 256;
	private static final int[] BAUD_RATES = {
		256000, 128000, 115200, 57600, 38400, 19200, 14400, 9600, 4800, 1200
	};
	private static final int OK_TIMEOUT = 5 * 1000; // Milliseconds
	private static final int CONNECT_TIMEOUT = 2 * 60 * 1000; // Milliseconds

	private final Executor executor;
	private final Callback callback;
	private final SerialPort port;
	private final Semaphore stateChange;
	private final byte[] line;

	private int lineLen = 0;

	private ReliabilityLayer reliabilityLayer = null; // Locking: this
	private boolean initialised = false, connected = false; // Locking: this

	ModemImpl(Executor executor, Callback callback, String portName) {
		this.executor = executor;
		this.callback = callback;
		port = new SerialPort(portName);
		stateChange = new Semaphore(1);
		line = new byte[MAX_LINE_LENGTH];
	}

	public boolean start() throws IOException {
		if(LOG.isLoggable(INFO)) LOG.info("Starting");
		try {
			stateChange.acquire();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to start");
		}
		try {
			// Open the serial port
			try {
				if(!port.openPort())
					throw new IOException("Failed to open serial port");
			} catch(SerialPortException e) {
				throw new IOException(e.toString());
			}
			// Find a suitable baud rate and initialise the modem
			try {
				boolean foundBaudRate = false;
				for(int baudRate : BAUD_RATES) {
					if(port.setParams(baudRate, 8, 1, 0)) {
						foundBaudRate = true;
						break;
					}
				}
				if(!foundBaudRate)
					throw new IOException("No suitable baud rate");
				port.purgePort(PURGE_RXCLEAR | PURGE_TXCLEAR);
				port.addEventListener(this);
				port.writeBytes("ATZ\r\n".getBytes("US-ASCII")); // Reset
				port.writeBytes("ATE0\r\n".getBytes("US-ASCII")); // Echo off
			} catch(SerialPortException e) {
				tryToClose(port);
				throw new IOException(e.toString());
			}
			// Wait for the event thread to receive "OK"
			try {
				synchronized(this) {
					long now = System.currentTimeMillis();
					long end = now + OK_TIMEOUT;
					while(now < end && !initialised) {
						wait(end - now);
						now = System.currentTimeMillis();
					}
					if(initialised) return true;
				}
			} catch(InterruptedException e) {
				tryToClose(port);
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while initialising");
			}
			tryToClose(port);
			return false;
		} finally {
			stateChange.release();
		}
	}

	private void tryToClose(SerialPort port) {
		try {
			port.closePort();
		} catch(SerialPortException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	public void stop() throws IOException {
		if(LOG.isLoggable(INFO)) LOG.info("Stopping");
		// Wake any threads that are waiting to connect
		synchronized(this) {
			initialised = false;
			connected = false;
			notifyAll();
		}
		// Hang up if necessary and close the port
		try {
			stateChange.acquire();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to stop");
		}
		try {
			hangUpInner();
			try {
				port.closePort();
			} catch(SerialPortException e) {
				throw new IOException(e.toString());
			}
		} finally {
			stateChange.release();
		}
	}

	// Locking: stateChange
	private void hangUpInner() throws IOException {
		ReliabilityLayer reliabilityLayer;
		synchronized(this) {
			if(this.reliabilityLayer == null) {
				if(LOG.isLoggable(INFO))
					LOG.info("Not hanging up - already on the hook");
				return;
			}
			reliabilityLayer = this.reliabilityLayer;
			this.reliabilityLayer = null;
			connected = false;
		}
		reliabilityLayer.stop();
		if(LOG.isLoggable(INFO)) LOG.info("Hanging up");
		try {
			port.setDTR(false);
		} catch(SerialPortException e) {
			tryToClose(port);
			throw new IOException(e.toString());
		}
	}

	public boolean dial(String number) throws IOException {
		if(!stateChange.tryAcquire()) {
			if(LOG.isLoggable(INFO))
				LOG.info("Not dialling - state change in progress");
			return false;
		}
		try {
			ReliabilityLayer reliabilityLayer =
					new ReliabilityLayer(executor, this);
			synchronized(this) {
				if(!initialised) {
					if(LOG.isLoggable(INFO))
						LOG.info("Not dialling - modem not initialised");
					return false;
				}
				if(this.reliabilityLayer != null) {
					if(LOG.isLoggable(INFO))
						LOG.info("Not dialling - call in progress");
					return false;
				}
				this.reliabilityLayer = reliabilityLayer;
			}
			reliabilityLayer.start();
			if(LOG.isLoggable(INFO)) LOG.info("Dialling");
			try {
				String dial = "ATDT" + number + "\r\n";
				port.writeBytes(dial.getBytes("US-ASCII"));
			} catch(SerialPortException e) {
				tryToClose(port);
				throw new IOException(e.toString());
			}
			// Wait for the event thread to receive "CONNECT"
			try {
				synchronized(this) {
					long now = System.currentTimeMillis();
					long end = now + CONNECT_TIMEOUT;
					while(now < end && initialised && !connected) {
						wait(end - now);
						now = System.currentTimeMillis();
					}
					if(connected) return true;
				}
			} catch(InterruptedException e) {
				tryToClose(port);
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while dialling");
			}
			hangUpInner();
			return false;
		} finally {
			stateChange.release();
		}
	}

	public InputStream getInputStream() throws IOException {
		ReliabilityLayer reliabilityLayer;
		synchronized(this) {
			reliabilityLayer = this.reliabilityLayer;
		}
		if(reliabilityLayer == null) throw new IOException("Not connected");
		return reliabilityLayer.getInputStream();
	}

	public OutputStream getOutputStream() throws IOException {
		ReliabilityLayer reliabilityLayer;
		synchronized(this) {
			reliabilityLayer = this.reliabilityLayer;
		}
		if(reliabilityLayer == null) throw new IOException("Not connected");
		return reliabilityLayer.getOutputStream();
	}

	public void hangUp() throws IOException {
		try {
			stateChange.acquire();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to hang up");
		}
		try {
			hangUpInner();
		} finally {
			stateChange.release();
		}
	}

	public void handleWrite(byte[] b) throws IOException {
		try {
			port.writeBytes(b);
		} catch(SerialPortException e) {
			tryToClose(port);
			throw new IOException(e.toString());
		}
	}

	public void serialEvent(SerialPortEvent ev) {
		try {
			if(ev.isRXCHAR()) {
				byte[] b = port.readBytes();
				if(!handleData(b)) handleText(b);
			} else if(ev.isDSR() && ev.getEventValue() == 0) {
				if(LOG.isLoggable(INFO)) LOG.info("Remote end hung up");
				hangUp();
			} else {
				if(LOG.isLoggable(INFO)) {
					LOG.info("Serial event " + ev.getEventType() + " " +
							ev.getEventValue());
				}
			}
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		} catch(SerialPortException e) {
			if(LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private boolean handleData(byte[] b) throws IOException {
		ReliabilityLayer reliabilityLayer;
		synchronized(this) {
			reliabilityLayer = this.reliabilityLayer;
		}
		if(reliabilityLayer == null) return false;
		reliabilityLayer.handleRead(b);
		return true;
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
					synchronized(this) {
						connected = true;
						notifyAll();
					}
					// There might be data in the buffer as well as text
					int off = i + 1;
					if(off < b.length) {
						byte[] data = new byte[b.length - off];
						System.arraycopy(b, off, data, 0, data.length);
						handleData(data);
					}
					return;
				} else if(s.equals("BUSY") || s.equals("NO DIALTONE")
						|| s.equals("NO CARRIER")) {
					synchronized(this) {
						connected = false;
						notifyAll();
					}
				} else if(s.equals("OK")) {
					synchronized(this) {
						initialised = true;
						notifyAll();
					}
				} else if(s.equals("RING")) {
					executor.execute(new Runnable() {
						public void run() {
							try {
								answer();
							} catch(IOException e) {
								if(LOG.isLoggable(WARNING))
									LOG.log(WARNING, e.toString(), e);
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
		if(!stateChange.tryAcquire()) {
			if(LOG.isLoggable(INFO))
				LOG.info("Not answering - state change in progress");
			return;
		}
		try {
			ReliabilityLayer reliabilityLayer =
					new ReliabilityLayer(executor, this);
			synchronized(this) {
				if(!initialised) {
					if(LOG.isLoggable(INFO))
						LOG.info("Not answering - modem not initialised");
					return;
				}
				if(this.reliabilityLayer != null) {
					if(LOG.isLoggable(INFO))
						LOG.info("Not answering - call in progress");
					return;
				}
				this.reliabilityLayer = reliabilityLayer;
			}
			reliabilityLayer.start();
			if(LOG.isLoggable(INFO)) LOG.info("Answering");
			try {
				port.writeBytes("ATA\r\n".getBytes("US-ASCII"));
			} catch(SerialPortException e) {
				tryToClose(port);
				throw new IOException(e.toString());
			}
			// Wait for the event thread to receive "CONNECT"
			boolean success = false;
			try {
				synchronized(this) {
					long now = System.currentTimeMillis();
					long end = now + CONNECT_TIMEOUT;
					while(now < end && initialised && !connected) {
						wait(end - now);
						now = System.currentTimeMillis();
					}
					success = connected;
				}
			} catch(InterruptedException e) {
				tryToClose(port);
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while answering");
			}
			if(success) callback.incomingCallConnected();
			else hangUpInner();
		} finally {
			stateChange.release();
		}
	}
}
