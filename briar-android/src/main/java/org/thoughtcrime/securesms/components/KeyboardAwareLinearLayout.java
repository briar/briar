package org.thoughtcrime.securesms.components;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.UiThread;
import android.util.AttributeSet;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;

import org.briarproject.briar.R;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.content.Context.WINDOW_SERVICE;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

/**
 * RelativeLayout that, when a view container, will report back when it thinks
 * a soft keyboard has been opened and what its height would be.
 */
@UiThread
public class KeyboardAwareLinearLayout extends LinearLayout {

	private static final Logger LOG =
			Logger.getLogger(KeyboardAwareLinearLayout.class.getName());

	private final Rect rect = new Rect();
	private final Set<OnKeyboardHiddenListener> hiddenListeners =
			new HashSet<>();
	private final Set<OnKeyboardShownListener> shownListeners = new HashSet<>();
	private final int minKeyboardSize;
	private final int minCustomKeyboardSize;
	private final int defaultCustomKeyboardSize;
	private final int minCustomKeyboardTopMargin;
	private final int statusBarHeight;

	private int viewInset;

	private boolean keyboardOpen = false;
	private int rotation = -1;

	public KeyboardAwareLinearLayout(Context context) {
		this(context, null);
	}

	public KeyboardAwareLinearLayout(Context context,
			@Nullable AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public KeyboardAwareLinearLayout(Context context,
			@Nullable AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		rotation = getDeviceRotation();
		final int statusBarRes = getResources()
				.getIdentifier("status_bar_height", "dimen", "android");
		minKeyboardSize =
				getResources().getDimensionPixelSize(R.dimen.min_keyboard_size);
		minCustomKeyboardSize = getResources()
				.getDimensionPixelSize(R.dimen.min_custom_keyboard_size);
		defaultCustomKeyboardSize = getResources()
				.getDimensionPixelSize(R.dimen.default_custom_keyboard_size);
		minCustomKeyboardTopMargin = getResources()
				.getDimensionPixelSize(R.dimen.min_custom_keyboard_top_margin);
		statusBarHeight = statusBarRes > 0 ?
				getResources().getDimensionPixelSize(statusBarRes) : 0;
		viewInset = getViewInset();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		updateRotation();
		updateKeyboardState();
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	}

	private void updateRotation() {
		int oldRotation = rotation;
		rotation = getDeviceRotation();
		if (oldRotation != rotation) {
			LOG.info("Rotation changed");
			onKeyboardClose();
		}
	}

	private void updateKeyboardState() {
		if (isLandscape()) {
			if (keyboardOpen) onKeyboardClose();
			return;
		}

		if (viewInset == 0 && Build.VERSION.SDK_INT >= 21)
			viewInset = getViewInset();
		int availableHeight =
				getRootView().getHeight() - statusBarHeight - viewInset;
		getWindowVisibleDisplayFrame(rect);

		int keyboardHeight = availableHeight - (rect.bottom - rect.top);

		if (keyboardHeight > minKeyboardSize) {
			if (getKeyboardHeight() != keyboardHeight)
				setKeyboardPortraitHeight(keyboardHeight);
			if (!keyboardOpen) onKeyboardOpen(keyboardHeight);
		} else if (keyboardOpen) {
			onKeyboardClose();
		}
	}

	@TargetApi(21)
	private int getViewInset() {
		try {
			Field attachInfoField = View.class.getDeclaredField("mAttachInfo");
			attachInfoField.setAccessible(true);
			Object attachInfo = attachInfoField.get(this);
			if (attachInfo != null) {
				Field stableInsetsField =
						attachInfo.getClass().getDeclaredField("mStableInsets");
				stableInsetsField.setAccessible(true);
				Rect insets = (Rect) stableInsetsField.get(attachInfo);
				return insets.bottom;
			}
		} catch (NoSuchFieldException e) {
			LOG.log(WARNING,
					"field reflection error when measuring view inset", e);
		} catch (IllegalAccessException e) {
			LOG.log(WARNING,
					"access reflection error when measuring view inset", e);
		}
		return 0;
	}

	protected void onKeyboardOpen(int keyboardHeight) {
		if (LOG.isLoggable(INFO))
			LOG.info("onKeyboardOpen(" + keyboardHeight + ")");
		keyboardOpen = true;

		notifyShownListeners();
	}

	protected void onKeyboardClose() {
		LOG.info("onKeyboardClose()");
		keyboardOpen = false;
		notifyHiddenListeners();
	}

	public boolean isKeyboardOpen() {
		return keyboardOpen;
	}

	public int getKeyboardHeight() {
		return isLandscape() ? getKeyboardLandscapeHeight() :
				getKeyboardPortraitHeight();
	}

	public boolean isLandscape() {
		int rotation = getDeviceRotation();
		return rotation == ROTATION_90 || rotation == ROTATION_270;
	}

	private int getDeviceRotation() {
		WindowManager windowManager =
				(WindowManager) getContext().getSystemService(WINDOW_SERVICE);
		return windowManager.getDefaultDisplay().getRotation();
	}

	private int getKeyboardLandscapeHeight() {
		return Math.max(getHeight(), getRootView().getHeight()) / 2;
	}

	private int getKeyboardPortraitHeight() {
		SharedPreferences prefs =
				PreferenceManager.getDefaultSharedPreferences(getContext());
		int keyboardHeight = prefs.getInt("keyboard_height_portrait",
				defaultCustomKeyboardSize);
		return clamp(keyboardHeight, minCustomKeyboardSize,
				getRootView().getHeight() - minCustomKeyboardTopMargin);
	}

	private int clamp(int value, int min, int max) {
		return Math.min(Math.max(value, min), max);
	}

	private void setKeyboardPortraitHeight(int height) {
		SharedPreferences prefs =
				PreferenceManager.getDefaultSharedPreferences(getContext());
		prefs.edit().putInt("keyboard_height_portrait", height).apply();
	}

	public void postOnKeyboardClose(final Runnable runnable) {
		if (keyboardOpen) {
			addOnKeyboardHiddenListener(new OnKeyboardHiddenListener() {
				@Override
				public void onKeyboardHidden() {
					removeOnKeyboardHiddenListener(this);
					runnable.run();
				}
			});
		} else {
			runnable.run();
		}
	}

	public void postOnKeyboardOpen(final Runnable runnable) {
		if (!keyboardOpen) {
			addOnKeyboardShownListener(new OnKeyboardShownListener() {
				@Override
				public void onKeyboardShown() {
					removeOnKeyboardShownListener(this);
					runnable.run();
				}
			});
		} else {
			runnable.run();
		}
	}

	public void addOnKeyboardHiddenListener(OnKeyboardHiddenListener listener) {
		hiddenListeners.add(listener);
	}

	public void removeOnKeyboardHiddenListener(
			OnKeyboardHiddenListener listener) {
		hiddenListeners.remove(listener);
	}

	public void addOnKeyboardShownListener(OnKeyboardShownListener listener) {
		shownListeners.add(listener);
	}

	public void removeOnKeyboardShownListener(
			OnKeyboardShownListener listener) {
		shownListeners.remove(listener);
	}

	private void notifyHiddenListeners() {
		// Make a copy as listeners may remove themselves when called
		Set<OnKeyboardHiddenListener> listeners =
				new HashSet<>(hiddenListeners);
		for (OnKeyboardHiddenListener listener : listeners) {
			listener.onKeyboardHidden();
		}
	}

	private void notifyShownListeners() {
		// Make a copy as listeners may remove themselves when called
		Set<OnKeyboardShownListener> listeners = new HashSet<>(shownListeners);
		for (OnKeyboardShownListener listener : listeners) {
			listener.onKeyboardShown();
		}
	}

	public interface OnKeyboardHiddenListener {
		void onKeyboardHidden();
	}

	public interface OnKeyboardShownListener {
		void onKeyboardShown();
	}
}
