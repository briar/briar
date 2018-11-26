package org.briarproject.briar.android.conversation;

import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog.Builder;
import android.support.v7.widget.Toolbar;
import android.transition.Fade;
import android.transition.Transition;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.conversation.glide.GlideApp;
import org.briarproject.briar.android.view.PullDownLayout;
import org.briarproject.briar.api.messaging.Attachment;
import org.briarproject.briar.api.messaging.MessagingManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import static android.content.Intent.ACTION_CREATE_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_TITLE;
import static android.graphics.Color.TRANSPARENT;
import static android.os.Build.VERSION.SDK_INT;
import static android.support.design.widget.Snackbar.LENGTH_LONG;
import static android.view.View.GONE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
import static android.widget.ImageView.ScaleType.FIT_START;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.copyAndClose;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_SAVE_ATTACHMENT;
import static org.briarproject.briar.android.util.UiUtils.formatDateAbsolute;

public class ImageActivity extends BriarActivity
		implements PullDownLayout.Callback {

	private final static Logger LOG = getLogger(ImageActivity.class.getName());

	final static String ATTACHMENT = "attachment";
	final static String NAME = "name";
	final static String DATE = "date";

	@Inject
	MessagingManager messagingManager;
	@Inject
	@IoExecutor
	Executor ioExecutor;

	private PullDownLayout layout;
	private AppBarLayout appBarLayout;
	private PhotoView photoView;
	private AttachmentItem attachment;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		// Transitions
		supportPostponeEnterTransition();
		Window window = getWindow();
		if (SDK_INT >= 21) {
			Transition transition = new Fade();
			setSceneTransitionAnimation(transition, null, transition);
		}

		// inflate layout
		setContentView(R.layout.activity_image);
		layout = findViewById(R.id.layout);
		layout.getBackground().setAlpha(255);
		layout.setCallback(this);

		// Status Bar
		if (SDK_INT >= 21) {
			window.setStatusBarColor(TRANSPARENT);
		} else if (SDK_INT >= 19) {
			// we can't make the status bar transparent, but translucent
			window.addFlags(FLAG_TRANSLUCENT_STATUS);
		}

		// Toolbar
		appBarLayout = findViewById(R.id.appBarLayout);
		Toolbar toolbar = requireNonNull(setUpCustomToolbar(true));
		TextView contactName = toolbar.findViewById(R.id.contactName);
		TextView dateView = toolbar.findViewById(R.id.dateView);

		// Intent Extras
		attachment = getIntent().getParcelableExtra(ATTACHMENT);
		String name = getIntent().getStringExtra(NAME);
		long time = getIntent().getLongExtra(DATE, 0);
		String date = formatDateAbsolute(this, time);
		contactName.setText(name);
		dateView.setText(date);

		// Image View
		photoView = findViewById(R.id.photoView);
		if (SDK_INT >= 16) {
			photoView.setOnClickListener(view -> toggleSystemUi());
			window.getDecorView().setSystemUiVisibility(
					SYSTEM_UI_FLAG_LAYOUT_STABLE |
							SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		}

		// Request Listener
		RequestListener<Drawable> listener = new RequestListener<Drawable>() {
			@Override
			public boolean onLoadFailed(@Nullable GlideException e,
					Object model, Target<Drawable> target,
					boolean isFirstResource) {
				supportStartPostponedEnterTransition();
				return false;
			}

			@Override
			public boolean onResourceReady(Drawable resource, Object model,
					Target<Drawable> target, DataSource dataSource,
					boolean isFirstResource) {
				if (SDK_INT >= 21 && !(resource instanceof Animatable)) {
					// set transition name only when not animatable,
					// because the animation won't start otherwise
					photoView.setTransitionName(
							attachment.getTransitionName());
				}
				// Move image to the top if overlapping toolbar
				if (isOverlappingToolbar(resource)) {
					photoView.setScaleType(FIT_START);
				}
				supportStartPostponedEnterTransition();
				return false;
			}
		};

		// Load Image
		GlideApp.with(this)
				.load(attachment)
				.diskCacheStrategy(NONE)
				.error(R.drawable.ic_image_broken)
				.dontTransform()
				.addListener(listener)
				.into(photoView);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.image_actions, menu);
		if (SDK_INT >= 19) {
			menu.findItem(R.id.action_save_image).setVisible(true);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			case R.id.action_save_image:
				if (SDK_INT >= 19) startSaveImage();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int request, int result, Intent data) {
		super.onActivityResult(request, result, data);
		if (request == REQUEST_SAVE_ATTACHMENT && result == RESULT_OK) {
			saveImage(data.getData());
		}
	}

	@Override
	public void onPullStart() {
		appBarLayout.animate()
				.alpha(0f)
				.start();
	}

	@Override
	public void onPull(float progress) {
		layout.getBackground().setAlpha(Math.round((1 - progress) * 255));
	}

	@Override
	public void onPullCancel() {
		appBarLayout.animate()
				.alpha(1f)
				.start();
	}

	@Override
	public void onPullComplete() {
		supportFinishAfterTransition();
	}

	@RequiresApi(api = 16)
	private void toggleSystemUi() {
		View decorView = getWindow().getDecorView();
		if (appBarLayout.getVisibility() == VISIBLE) {
			hideSystemUi(decorView);
		} else {
			showSystemUi(decorView);
		}
	}

	@RequiresApi(api = 16)
	private void hideSystemUi(View decorView) {
		decorView.setSystemUiVisibility(SYSTEM_UI_FLAG_LAYOUT_STABLE
				| SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
				| SYSTEM_UI_FLAG_FULLSCREEN
		);
		appBarLayout.animate()
				.translationYBy(-1 * appBarLayout.getHeight())
				.alpha(0f)
				.withEndAction(() -> appBarLayout.setVisibility(GONE))
				.start();
	}

	@RequiresApi(api = 16)
	private void showSystemUi(View decorView) {
		decorView.setSystemUiVisibility(
				SYSTEM_UI_FLAG_LAYOUT_STABLE
						| SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
		);
		appBarLayout.animate()
				.translationYBy(appBarLayout.getHeight())
				.alpha(1f)
				.withStartAction(() -> appBarLayout.setVisibility(VISIBLE))
				.start();
	}

	private boolean isOverlappingToolbar(Drawable drawable) {
		int width = drawable.getIntrinsicWidth();
		int height = drawable.getIntrinsicHeight();
		float widthPercentage = photoView.getWidth() / (float) width;
		float heightPercentage = photoView.getHeight() / (float) height;
		float scaleFactor = Math.min(widthPercentage, heightPercentage);
		int realWidth = (int) (width * scaleFactor);
		int realHeight = (int) (height * scaleFactor);
		// return if photo doesn't use the full width,
		// because it will be moved to the right otherwise
		if (realWidth < photoView.getWidth()) return false;
		int drawableTop = (photoView.getHeight() - realHeight) / 2;
		return drawableTop < appBarLayout.getBottom() &&
				drawableTop != appBarLayout.getTop();
	}

	@RequiresApi(api = 19)
	private void startSaveImage() {
		OnClickListener okListener = (dialog, which) -> {
			Intent intent = getCreationIntent();
			startActivityForResult(intent, REQUEST_SAVE_ATTACHMENT);
		};
		Builder builder = new Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.dialog_title_save_image));
		builder.setMessage(getString(R.string.dialog_message_save_image));
		builder.setIcon(R.drawable.emoji_google_1f6af);
		builder.setPositiveButton(R.string.save_image, okListener);
		builder.setNegativeButton(R.string.cancel, null);
		builder.show();
	}

	@RequiresApi(api = 19)
	private Intent getCreationIntent() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",
				Locale.getDefault());
		String fileName = sdf.format(new Date());
		Intent intent = new Intent(ACTION_CREATE_DOCUMENT);
		intent.addCategory(CATEGORY_OPENABLE);
		intent.setType(attachment.getMimeType());
		intent.putExtra(EXTRA_TITLE, fileName);
		return intent;
	}

	private void saveImage(@Nullable Uri uri) {
		if (uri == null) return;
		MessageId messageId = attachment.getMessageId();
		runOnDbThread(() -> {
			try {
				Attachment a = messagingManager.getAttachment(messageId);
				copyImageFromDb(a, uri);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				onImageSaveError();
			}
		});
	}

	private void copyImageFromDb(Attachment a, Uri uri) {
		ioExecutor.execute(() -> {
			try {
				InputStream is = a.getStream();
				OutputStream os = getContentResolver().openOutputStream(uri);
				if (os == null) throw new IOException();
				copyAndClose(is, os);
			} catch (IOException e) {
				logException(LOG, WARNING, e);
				onImageSaveError();
			}
		});
	}

	private void onImageSaveError() {
		Snackbar s =
				Snackbar.make(layout, R.string.save_image_error, LENGTH_LONG);
		s.getView().setBackgroundResource(R.color.briar_red);
		s.show();
	}

}
