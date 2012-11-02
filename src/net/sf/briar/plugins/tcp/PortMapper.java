package net.sf.briar.plugins.tcp;

interface PortMapper {

	void start();

	void stop();

	MappingResult map(int port);
}
