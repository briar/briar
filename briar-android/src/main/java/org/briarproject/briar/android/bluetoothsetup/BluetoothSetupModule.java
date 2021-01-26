package org.briarproject.briar.android.bluetoothsetup;

import org.briarproject.briar.android.viewmodel.ViewModelKey;

import androidx.lifecycle.ViewModel;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoMap;

@Module
public abstract class BluetoothSetupModule {

	@Binds
	@IntoMap
	@ViewModelKey(BluetoothSetupViewModel.class)
	abstract ViewModel bindBluetoothSetupViewModel(
			BluetoothSetupViewModel bluetoothSetupViewModel);
}
