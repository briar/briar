package org.briarproject.briar.android.blog;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public interface BlogModule {

	@Binds
	@IntoMap
	@ViewModelKey(FeedViewModel.class)
	abstract ViewModel bindFeedViewModel(FeedViewModel feedViewModel);

	@Binds
	@IntoMap
	@ViewModelKey(BlogViewModel.class)
	abstract ViewModel bindBlogViewModel(BlogViewModel blogViewModel);

}
