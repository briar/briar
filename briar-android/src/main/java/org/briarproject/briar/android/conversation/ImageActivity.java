package org.briarproject.briar.android.conversation;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.design.widget.AppBarLayout;
import android.support.v7.widget.Toolbar;
import android.transition.Fade;
import android.transition.Transition;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.chrisbanes.photoview.PhotoView;

import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;
import org.briarproject.briar.android.conversation.glide.GlideApp;
import org.briarproject.briar.android.view.PullDownLayout;

import static android.graphics.Color.TRANSPARENT;
import static android.os.Build.VERSION.SDK_INT;
import static android.view.View.GONE;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
import static android.view.View.VISIBLE;
import static android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS;
import static com.bumptech.glide.load.engine.DiskCacheStrategy.NONE;
import static java.util.Objects.requireNonNull;
import static org.briarproject.briar.android.util.UiUtils.formatDateAbsolute;

public class ImageActivity extends BriarActivity
		implements PullDownLayout.Callback {

	final static String ATTACHMENT = "attachment";
	final static String NAME = "name";
	final static String DATE = "date";

	private PullDownLayout layout;
	private AppBarLayout appBarLayout;

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
		AttachmentItem attachment = getIntent().getParcelableExtra(ATTACHMENT);
		String name = getIntent().getStringExtra(NAME);
		long time = getIntent().getLongExtra(DATE, 0);
		String date = formatDateAbsolute(this, time);
		contactName.setText(name);
		dateView.setText(date);

		// Image View
		PhotoView photoView = findViewById(R.id.photoView);
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
				return false;
			}

			@Override
			public boolean onResourceReady(Drawable resource,
					Object model, Target<Drawable> target,
					DataSource dataSource, boolean isFirstResource) {
				if (SDK_INT >= 21 &&
						!(resource instanceof Animatable)) {
					// set transition name only when not animatable,
					// because the animation won't start otherwise
					photoView.setTransitionName(
							attachment.getTransitionName());
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
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
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
			hideSystemUI(decorView);
		} else {
			showSystemUI(decorView);
		}
	}

	@RequiresApi(api = 16)
	private void hideSystemUI(View decorView) {
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
	private void showSystemUI(View decorView) {
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

}
