package org.thoughtcrime.securesms.components.emoji;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.astuetz.PagerSlidingTabStrip;
import com.astuetz.PagerSlidingTabStrip.CustomTabProvider;

import org.briarproject.briar.R;
import org.thoughtcrime.securesms.components.RepeatableImageKey;
import org.thoughtcrime.securesms.components.RepeatableImageKey.KeyEventListener;
import org.thoughtcrime.securesms.components.emoji.EmojiPageView.EmojiSelectionListener;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_DEL;
import static android.widget.ImageView.ScaleType.CENTER_INSIDE;
import static java.util.logging.Level.INFO;

@UiThread
public class EmojiDrawer extends LinearLayout {

	private static final Logger LOG =
			Logger.getLogger(EmojiDrawer.class.getName());
	private static final KeyEvent DELETE_KEY_EVENT =
			new KeyEvent(ACTION_DOWN, KEYCODE_DEL);

	private ViewPager pager;
	private List<EmojiPageModel> models;
	private PagerSlidingTabStrip strip;
	private RecentEmojiPageModel recentModel;
	private EmojiEventListener listener;
	private EmojiDrawerListener drawerListener;

	public EmojiDrawer(Context context) {
		this(context, null);
	}

	public EmojiDrawer(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
		setOrientation(VERTICAL);
	}

	private void initView() {
		final View v = LayoutInflater.from(getContext())
				.inflate(R.layout.emoji_drawer, this, true);
		initializeResources(v);
		initializePageModels();
		initializeEmojiGrid();
	}

	public void setEmojiEventListener(EmojiEventListener listener) {
		this.listener = listener;
	}

	public void setDrawerListener(EmojiDrawerListener listener) {
		this.drawerListener = listener;
	}

	private void initializeResources(View v) {
		this.pager = (ViewPager) v.findViewById(R.id.emoji_pager);
		this.strip = (PagerSlidingTabStrip) v.findViewById(R.id.tabs);

		RepeatableImageKey backspace =
				(RepeatableImageKey) v.findViewById(R.id.backspace);
		backspace.setOnKeyEventListener(new KeyEventListener() {
			@Override
			public void onKeyEvent() {
				if (listener != null) listener.onKeyEvent(DELETE_KEY_EVENT);
			}
		});
	}

	public boolean isShowing() {
		return getVisibility() == VISIBLE;
	}

	public void show(int height) {
		if (this.pager == null) initView();
		ViewGroup.LayoutParams params = getLayoutParams();
		params.height = height;
		if (LOG.isLoggable(INFO))
			LOG.info("Showing emoji drawer with height " + params.height);
		setLayoutParams(params);
		setVisibility(VISIBLE);
		if (drawerListener != null) drawerListener.onShown();
	}

	public void hide() {
		setVisibility(GONE);
		if (drawerListener != null) drawerListener.onHidden();
	}

	private void initializeEmojiGrid() {
		pager.setAdapter(new EmojiPagerAdapter(getContext(),
				models,
				new EmojiSelectionListener() {
					@Override
					public void onEmojiSelected(String emoji) {
						recentModel.onCodePointSelected(emoji);
						if (listener != null) listener.onEmojiSelected(emoji);
					}
				}));

		if (recentModel.getEmoji().length == 0) {
			pager.setCurrentItem(1);
		}
		strip.setViewPager(pager);
	}

	private void initializePageModels() {
		this.models = new LinkedList<>();
		this.recentModel = new RecentEmojiPageModel(getContext());
		this.models.add(recentModel);
		this.models.addAll(EmojiProvider.getInstance(getContext())
				.getStaticPages());
	}

	public static class EmojiPagerAdapter extends PagerAdapter
			implements CustomTabProvider {
		private Context context;
		private List<EmojiPageModel> pages;
		private EmojiSelectionListener listener;

		private EmojiPagerAdapter(@NonNull Context context,
				@NonNull List<EmojiPageModel> pages,
				@Nullable EmojiSelectionListener listener) {
			super();
			this.context = context;
			this.pages = pages;
			this.listener = listener;
		}

		@Override
		public int getCount() {
			return pages.size();
		}

		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			EmojiPageView page = new EmojiPageView(context);
			page.setModel(pages.get(position));
			page.setEmojiSelectedListener(listener);
			container.addView(page);
			return page;
		}

		@Override
		public void destroyItem(ViewGroup container, int position,
				Object object) {
			container.removeView((View) object);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}

		@Override
		public View getCustomTabView(ViewGroup viewGroup, int i) {
			ImageView image = new ImageView(context);
			image.setScaleType(CENTER_INSIDE);
			image.setImageResource(pages.get(i).getIcon());
			return image;
		}

		@Override
		public void tabSelected(View view) {
			view.animate().setDuration(300).alpha(1);
		}

		@Override
		public void tabUnselected(View view) {
			view.animate().setDuration(400).alpha(0.4f);
		}
	}

	public interface EmojiEventListener extends EmojiSelectionListener {
		void onKeyEvent(KeyEvent keyEvent);
	}

	public interface EmojiDrawerListener {
		void onShown();

		void onHidden();
	}
}
