package org.briarproject.bramble.system;

import javax.annotation.Nullable;
import java.security.Provider;

public class WindowsSecureRandomProvider extends AbstractSecureRandomProvider {
    @Nullable
    @Override
    public Provider getProvider() {
        return null;
    }
}
