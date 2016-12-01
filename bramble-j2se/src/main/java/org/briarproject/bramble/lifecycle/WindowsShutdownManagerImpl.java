package org.briarproject.bramble.lifecycle;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HINSTANCE;
import com.sun.jna.platform.win32.WinDef.HMENU;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser.MSG;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.OsUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.annotation.concurrent.ThreadSafe;

import static com.sun.jna.Library.OPTION_FUNCTION_MAPPER;
import static com.sun.jna.Library.OPTION_TYPE_MAPPER;
import static java.util.logging.Level.WARNING;

@ThreadSafe
@NotNullByDefault
class WindowsShutdownManagerImpl extends ShutdownManagerImpl {

	private static final Logger LOG =
			Logger.getLogger(WindowsShutdownManagerImpl.class.getName());

	private static final int WM_QUERYENDSESSION = 17;
	private static final int GWL_WNDPROC = -4;
	private static final int WS_MINIMIZE = 0x20000000;

	private final Map<String, Object> options;

	private boolean initialised = false; // Locking: lock

	WindowsShutdownManagerImpl() {
		// Use the Unicode versions of Win32 API calls
		Map<String, Object> m = new HashMap<>();
		m.put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
		m.put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
		options = Collections.unmodifiableMap(m);
	}

	@Override
	public int addShutdownHook(Runnable r) {
		lock.lock();
		try {
			if (!initialised) initialise();
			return super.addShutdownHook(r);
		} finally {
			lock.unlock();
		}
	}

	@Override
	protected Thread createThread(Runnable r) {
		return new StartOnce(r);
	}

	// Locking: lock
	private void initialise() {
		if (OsUtils.isWindows()) {
			new EventLoop().start();
		} else {
			LOG.warning("Windows shutdown manager used on non-Windows OS");
		}
		initialised = true;
	}

	// Package access for testing
	void runShutdownHooks() {
		lock.lock();
		try {
			boolean interrupted = false;
			// Start each hook in its own thread
			for (Thread hook : hooks.values()) hook.start();
			// Wait for all the hooks to finish
			for (Thread hook : hooks.values()) {
				try {
					hook.join();
				} catch (InterruptedException e) {
					LOG.warning("Interrupted while running shutdown hooks");
					interrupted = true;
				}
			}
			if (interrupted) Thread.currentThread().interrupt();
		} finally {
			lock.unlock();
		}
	}

	private class EventLoop extends Thread {

		private EventLoop() {
			super("EventLoop");
		}

		@Override
		public void run() {
			try {
				// Load user32.dll
				final User32 user32 = (User32) Native.loadLibrary("user32",
						User32.class, options);
				// Create a callback to handle the WM_QUERYENDSESSION message
				WindowProc proc = new WindowProc() {
					@Override
					public LRESULT callback(HWND hwnd, int msg, WPARAM wp,
							LPARAM lp) {
						if (msg == WM_QUERYENDSESSION) {
							// It's safe to delay returning from this message
							runShutdownHooks();
						}
						// Pass the message to the default window procedure
						return user32.DefWindowProc(hwnd, msg, wp, lp);
					}
				};
				// Create a native window
				HWND hwnd = user32.CreateWindowEx(0, "STATIC", "", WS_MINIMIZE,
						0, 0, 0, 0, null, null, null, null);
				// Register the callback
				try {
					// Use SetWindowLongPtr if available (64-bit safe)
					user32.SetWindowLongPtr(hwnd, GWL_WNDPROC, proc);
					LOG.info("Registered 64-bit callback");
				} catch (UnsatisfiedLinkError e) {
					// Use SetWindowLong if SetWindowLongPtr isn't available
					user32.SetWindowLong(hwnd, GWL_WNDPROC, proc);
					LOG.info("Registered 32-bit callback");
				}
				// Handle events until the window is destroyed
				MSG msg = new MSG();
				while (user32.GetMessage(msg, null, 0, 0) > 0) {
					user32.TranslateMessage(msg);
					user32.DispatchMessage(msg);
				}
			} catch (UnsatisfiedLinkError e) {
				if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
			}
		}
	}

	private static class StartOnce extends Thread {

		private final AtomicBoolean called = new AtomicBoolean(false);

		private StartOnce(Runnable r) {
			super(r, "ShutdownManager");
		}

		@Override
		public void start() {
			// Ensure the thread is only started once
			if (!called.getAndSet(true)) super.start();
		}
	}

	private interface User32 extends StdCallLibrary {

		HWND CreateWindowEx(int styleEx, String className, String windowName,
				int style, int x, int y, int width, int height, HWND parent,
				HMENU menu, HINSTANCE instance, Pointer param);

		LRESULT DefWindowProc(HWND hwnd, int msg, WPARAM wp, LPARAM lp);

		LRESULT SetWindowLong(HWND hwnd, int index, WindowProc newProc);

		LRESULT SetWindowLongPtr(HWND hwnd, int index, WindowProc newProc);

		int GetMessage(MSG msg, HWND hwnd, int filterMin, int filterMax);

		boolean TranslateMessage(MSG msg);

		LRESULT DispatchMessage(MSG msg);
	}

	private interface WindowProc extends StdCallCallback {

		LRESULT callback(HWND hwnd, int msg, WPARAM wp, LPARAM lp);
	}
}
