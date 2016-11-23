package org.briarproject.bramble.plugin.tcp;

import javax.annotation.Nullable;

interface PortMapper {

	@Nullable
	MappingResult map(int port);
}
