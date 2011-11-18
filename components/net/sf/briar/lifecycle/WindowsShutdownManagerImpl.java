package net.sf.briar.lifecycle;

import java.awt.HeadlessException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JFrame;

import net.sf.briar.util.OsUtils;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.StdCallLibrary.StdCallCallback;

class WindowsShutdownManagerImpl extends ShutdownManagerImpl {

	private static final Logger LOG =
		Logger.getLogger(WindowsShutdownManagerImpl.class.getName());

	private static final int WM_QUERYENDSESSION = 17;
	private static final int WM_ENDSESSION = 22;
	private static final int GWL_WNDPROC = -4;

	private boolean initialised = false; // Locking: this

	@Override
	public synchronized int addShutdownHook(Runnable runnable) {
		if(!initialised) initialise();
		return super.addShutdownHook(new RunOnce(runnable));
	}

	// Locking: this
	private void initialise() {
		if(OsUtils.isWindows()) {
			try {
				HWND hwnd = new HWND();
				hwnd.setPointer(Native.getComponentPointer(new JFrame()));
				User32 u = (User32) Native.loadLibrary("user32", User32.class);
				try {
					// Load the 64-bit functions
					setCallback64Bit(u, hwnd);
				} catch(UnsatisfiedLinkError e) {
					// Load the 32-bit functions
					setCallback32Bit(u, hwnd);
				}
			} catch(HeadlessException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			} catch(UnsatisfiedLinkError e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		} else {
			if(LOG.isLoggable(Level.WARNING))
				LOG.warning("Windows shutdown manager used on non-Windows OS");
		}
		initialised = true;
	}

	private void setCallback64Bit(final User32 user32, HWND hwnd) {
		final WindowProc oldProc = user32.GetWindowLongPtrW(hwnd, GWL_WNDPROC);
		WindowProc newProc = new WindowProc() {
			public LRESULT callback(HWND hwnd, int msg, WPARAM wp, LPARAM lp) {
				if(msg == WM_QUERYENDSESSION) {
					// It's safe to delay returning from this message
					runShutdownHooks();
				} else if(msg == WM_ENDSESSION) {
					// Return immediately or the JVM crashes on return
				}
				return user32.CallWindowProcPtrW(oldProc, hwnd, msg, wp, lp);
			}
		};
		user32.SetWindowLongPtrW(hwnd, GWL_WNDPROC, newProc);
	}

	private void setCallback32Bit(final User32 user32, HWND hwnd) {
		final WindowProc oldProc = user32.GetWindowLongW(hwnd, GWL_WNDPROC);
		WindowProc newProc = new WindowProc() {
			public LRESULT callback(HWND hwnd, int msg, WPARAM wp, LPARAM lp) {
				if(msg == WM_QUERYENDSESSION) {
					// It's safe to delay returning from this message
					runShutdownHooks();
				} else if(msg == WM_ENDSESSION) {
					// Return immediately or the JVM crashes on return
				}
				return user32.CallWindowProcW(oldProc, hwnd, msg, wp, lp);
			}
		};
		user32.SetWindowLongW(hwnd, GWL_WNDPROC, newProc);
	}

	// Package access for testing
	synchronized void runShutdownHooks() {
		// Start each hook in its own thread
		for(Thread hook : hooks.values()) hook.start();
		// Wait for all the hooks to finish
		for(Thread hook : hooks.values()) {
			try {
				hook.join();
			} catch(InterruptedException e) {
				if(LOG.isLoggable(Level.WARNING)) LOG.warning(e.getMessage());
			}
		}
	}

	private static class RunOnce implements Runnable {

		private final Runnable runnable;
		private final AtomicBoolean called = new AtomicBoolean(false);

		private RunOnce(Runnable runnable) {
			this.runnable = runnable;
		}

		public void run() {
			if(called.getAndSet(true)) return;
			runnable.run();
		}
	}

	private static interface User32 extends StdCallLibrary {

		LRESULT CallWindowProcW(WindowProc oldProc, HWND hwnd, int msg,
				WPARAM wp, LPARAM lp);
		LRESULT CallWindowProcPtrW(WindowProc oldProc, HWND hwnd, int msg,
				WPARAM wp, LPARAM lp);

		WindowProc GetWindowLongW(HWND hwnd, int index);
		WindowProc GetWindowLongPtrW(HWND hwnd, int index);

		LRESULT SetWindowLongW(HWND hwnd, int index, WindowProc newProc);
		LRESULT SetWindowLongPtrW(HWND hwnd, int index, WindowProc newProc);
	}

	private static interface WindowProc extends StdCallCallback {

		public LRESULT callback(HWND hwnd, int msg, WPARAM wp, LPARAM lp);
	}
}
