package org.briarproject.bramble.plugin.modem;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.reliability.ReliabilityLayer;
import org.briarproject.bramble.api.reliability.ReliabilityLayerFactory;
import org.briarproject.bramble.api.reliability.WriteHandler;
import org.briarproject.bramble.api.system.Clock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static jssc.SerialPort.PURGE_RXCLEAR;
import static jssc.SerialPort.PURGE_TXCLEAR;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
class ModemImpl implements Modem, WriteHandler, SerialPortEventListener {

	private static final Logger LOG =
			Logger.getLogger(ModemImpl.class.getName());
	private static final int MAX_LINE_LENGTH = 256;
	private static final int[] BAUD_RATES = {
		256000, 128000, 115200, 57600, 38400, 19200, 14400, 9600, 4800, 1200
	};
	private static final int OK_TIMEOUT = 5 * 1000; // Milliseconds
	private static final int CONNECT_TIMEOUT = 2 * 60 * 1000; // Milliseconds
	private static final int ESCAPE_SEQUENCE_GUARD_TIME = 1000; // Milliseconds

	private final Executor ioExecutor;
	private final ReliabilityLayerFactory reliabilityFactory;
	private final Clock clock;
	private final Callback callback;
	private final SerialPort port;
	private final Semaphore stateChange;
	private final byte[] line;

	private final Lock lock = new ReentrantLock();
	private final Condition connectedStateChanged = lock.newCondition();
	private final Condition initialisedStateChanged = lock.newCondition();

	// The following are locking: lock
	private ReliabilityLayer reliability = null;
	private boolean initialised = false, connected = false;

	private int lineLen = 0;

	ModemImpl(Executor ioExecutor, ReliabilityLayerFactory reliabilityFactory,
			Clock clock, Callback callback, SerialPort port) {
		this.ioExecutor = ioExecutor;
		this.reliabilityFactory = reliabilityFactory;
		this.clock = clock;
		this.callback = callback;
		this.port = port;
		stateChange = new Semaphore(1);
		line = new byte[MAX_LINE_LENGTH];
	}

	@Override
	public boolean start() throws IOException {
		LOG.info("Starting");
		try {
			stateChange.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to start");
		}
		try {
			// Open the serial port
			port.openPort();
			// Find a suitable baud rate and initialise the modem
			try {
				boolean foundBaudRate = false;
				for (int baudRate : BAUD_RATES) {
					if (port.setParams(baudRate, 8, 1, 0)) {
						foundBaudRate = true;
						break;
					}
				}
				if (!foundBaudRate) {
					tryToClose(port);
					throw new IOException("No suitable baud rate");
				}
				port.purgePort(PURGE_RXCLEAR | PURGE_TXCLEAR);
				port.addEventListener(this);
				port.writeBytes("ATZ\r\n".getBytes("US-ASCII")); // Reset
				port.writeBytes("ATE0\r\n".getBytes("US-ASCII")); // Echo off
			} catch (IOException e) {
				tryToClose(port);
				throw e;
			}
			// Wait for the event thread to receive "OK"
			boolean success = false;
			try {
				lock.lock();
				try {
					long now = clock.currentTimeMillis();
					long end = now + OK_TIMEOUT;
					while (now < end && !initialised) {
						initialisedStateChanged.await(end - now, MILLISECONDS);
						now = clock.currentTimeMillis();
					}
					success = initialised;
				} finally {
					lock.unlock();
				}
			} catch (InterruptedException e) {
				tryToClose(port);
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while initialising");
			}
			if (success) return true;
			tryToClose(port);
			return false;
		} finally {
			stateChange.release();
		}
	}

	private void tryToClose(@Nullable SerialPort port) {
		try {
			if (port != null) port.closePort();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	@Override
	public void stop() throws IOException {
		LOG.info("Stopping");
		lock.lock();
		try {
			// Wake any threads that are waiting to connect
			initialised = false;
			connected = false;
			initialisedStateChanged.signalAll();
			connectedStateChanged.signalAll();
		} finally {
			lock.unlock();
		}
		// Hang up if necessary and close the port
		try {
			stateChange.acquire();
		} catch (InterruptedException e) {
			tryToClose(port);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to stop");
		}
		try {
			hangUpInner();
			port.closePort();
		} finally {
			stateChange.release();
		}
	}

	// Locking: stateChange
	private void hangUpInner() throws IOException {
		ReliabilityLayer reliability;
		lock.lock();
		try {
			if (this.reliability == null) {
				LOG.info("Not hanging up - already on the hook");
				return;
			}
			reliability = this.reliability;
			this.reliability = null;
			connected = false;
		} finally {
			lock.unlock();
		}
		reliability.stop();
		LOG.info("Hanging up");
		try {
			clock.sleep(ESCAPE_SEQUENCE_GUARD_TIME);
			port.writeBytes("+++".getBytes("US-ASCII"));
			clock.sleep(ESCAPE_SEQUENCE_GUARD_TIME);
			port.writeBytes("ATH\r\n".getBytes("US-ASCII"));
		} catch (InterruptedException e) {
			tryToClose(port);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while hanging up");
		} catch (IOException e) {
			tryToClose(port);
			throw e;
		}
	}

	@Override
	public boolean dial(String number) throws IOException {
		if (!stateChange.tryAcquire()) {
			LOG.info("Not dialling - state change in progress");
			return false;
		}
		try {
			ReliabilityLayer reliability =
					reliabilityFactory.createReliabilityLayer(this);
			lock.lock();
			try {
				if (!initialised) {
					LOG.info("Not dialling - modem not initialised");
					return false;
				}
				if (this.reliability != null) {
					LOG.info("Not dialling - call in progress");
					return false;
				}
				this.reliability = reliability;
			} finally {
				lock.unlock();
			}
			reliability.start();
			LOG.info("Dialling");
			try {
				String dial = "ATDT" + number + "\r\n";
				port.writeBytes(dial.getBytes("US-ASCII"));
			} catch (IOException e) {
				tryToClose(port);
				throw e;
			}
			// Wait for the event thread to receive "CONNECT"
			try {
				lock.lock();
				try {
					long now = clock.currentTimeMillis();
					long end = now + CONNECT_TIMEOUT;
					while (now < end && initialised && !connected) {
						connectedStateChanged.await(end - now, MILLISECONDS);
						now = clock.currentTimeMillis();
					}
					if (connected) return true;
				} finally {
					lock.unlock();
				}
			} catch (InterruptedException e) {
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

	@Override
	public InputStream getInputStream() throws IOException {
		ReliabilityLayer reliability;
		lock.lock();
		try {
			reliability = this.reliability;
		} finally {
			lock.unlock();
		}
		if (reliability == null) throw new IOException("Not connected");
		return reliability.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		ReliabilityLayer reliability;
		lock.lock();
		try {
			reliability = this.reliability;
		} finally {
			lock.unlock();
		}
		if (reliability == null) throw new IOException("Not connected");
		return reliability.getOutputStream();
	}

	@Override
	public void hangUp() throws IOException {
		try {
			stateChange.acquire();
		} catch (InterruptedException e) {
			tryToClose(port);
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while waiting to hang up");
		}
		try {
			hangUpInner();
		} finally {
			stateChange.release();
		}
	}

	@Override
	public void handleWrite(byte[] b) throws IOException {
		try {
			port.writeBytes(b);
		} catch (IOException e) {
			tryToClose(port);
			throw e;
		}
	}

	@Override
	public void serialEvent(SerialPortEvent ev) {
		try {
			if (ev.isRXCHAR()) {
				byte[] b = port.readBytes();
				if (!handleData(b)) handleText(b);
			} else if (ev.isDSR() && ev.getEventValue() == 0) {
				LOG.info("Remote end hung up");
				hangUp();
			} else {
				if (LOG.isLoggable(INFO)) {
					LOG.info("Serial event " + ev.getEventType() + " " +
							ev.getEventValue());
				}
			}
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private boolean handleData(byte[] b) throws IOException {
		ReliabilityLayer reliability;
		lock.lock();
		try {
			reliability = this.reliability;
		} finally {
			lock.unlock();
		}
		if (reliability == null) return false;
		reliability.handleRead(b);
		return true;
	}

	private void handleText(byte[] b) throws IOException {
		if (lineLen + b.length > MAX_LINE_LENGTH) {
			tryToClose(port);
			throw new IOException("Line too long");
		}
		for (int i = 0; i < b.length; i++) {
			line[lineLen] = b[i];
			if (b[i] == '\n') {
				// FIXME: Use CharsetDecoder to catch invalid ASCII
				String s = new String(line, 0, lineLen, "US-ASCII").trim();
				lineLen = 0;
				if (LOG.isLoggable(INFO)) LOG.info("Modem status: " + s);
				if (s.startsWith("CONNECT")) {
					lock.lock();
					try {
						connected = true;
						connectedStateChanged.signalAll();
					} finally {
						lock.unlock();
					}
					// There might be data in the buffer as well as text
					int off = i + 1;
					if (off < b.length) {
						byte[] data = new byte[b.length - off];
						System.arraycopy(b, off, data, 0, data.length);
						handleData(data);
					}
					return;
				} else if (s.equals("BUSY") || s.equals("NO DIALTONE")
						|| s.equals("NO CARRIER")) {
					lock.lock();
					try {
						connected = false;
						connectedStateChanged.signalAll();
					} finally {
						lock.unlock();
					}
				} else if (s.equals("OK")) {
					lock.lock();
					try {
						initialised = true;
						initialisedStateChanged.signalAll();
					} finally {
						lock.unlock();
					}
				} else if (s.equals("RING")) {
					ioExecutor.execute(new Runnable() {
						@Override
						public void run() {
							try {
								answer();
							} catch (IOException e) {
								if (LOG.isLoggable(WARNING))
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
		if (!stateChange.tryAcquire()) {
			LOG.info("Not answering - state change in progress");
			return;
		}
		try {
			ReliabilityLayer reliability =
					reliabilityFactory.createReliabilityLayer(this);
			lock.lock();
			try {
				if (!initialised) {
					LOG.info("Not answering - modem not initialised");
					return;
				}
				if (this.reliability != null) {
					LOG.info("Not answering - call in progress");
					return;
				}
				this.reliability = reliability;
			} finally {
				lock.unlock();
			}
			reliability.start();
			LOG.info("Answering");
			try {
				port.writeBytes("ATA\r\n".getBytes("US-ASCII"));
			} catch (IOException e) {
				tryToClose(port);
				throw e;
			}
			// Wait for the event thread to receive "CONNECT"
			boolean success = false;
			try {
				lock.lock();
				try {
					long now = clock.currentTimeMillis();
					long end = now + CONNECT_TIMEOUT;
					while (now < end && initialised && !connected) {
						connectedStateChanged.await(end - now, MILLISECONDS);
						now = clock.currentTimeMillis();
					}
					success = connected;
				} finally {
					lock.unlock();
				}
			} catch (InterruptedException e) {
				tryToClose(port);
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while answering");
			}
			if (success) callback.incomingCallConnected();
			else hangUpInner();
		} finally {
			stateChange.release();
		}
	}
}
