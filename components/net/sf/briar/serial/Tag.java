package net.sf.briar.serial;

interface Tag {

	static final byte FALSE = (byte) 0xFF; // 1111 1111
	static final byte TRUE = (byte) 0xFE; // 1111 1110
	static final byte INT8 = (byte) 0xFD; // 1111 1101
	static final byte INT16 = (byte) 0xFC; // 1111 1100
	static final byte INT32 = (byte) 0xFB; // 1111 1011
	static final byte INT64 = (byte) 0xFA; // 1111 1010
	static final byte FLOAT32 = (byte) 0xF9; // 1111 1001
	static final byte FLOAT64 = (byte) 0xF8; // 1111 1000
	static final byte STRING = (byte) 0xF7; // 1111 0111
	static final byte BYTES = (byte) 0xF6; // 1111 0110
	static final byte LIST = (byte) 0xF5; // 1111 0111
	static final byte MAP = (byte) 0xF4; // 1111 0100
	static final byte END = (byte) 0xF3; // 1111 0011
	static final byte NULL = (byte) 0xF2; // 1111 0010
	static final byte STRUCT = (byte) 0xF1; // 1111 0001

	static final int SHORT_STRING = 0x80; // 1000 xxxx
	static final int SHORT_BYTES = 0x90; // 1001 xxxx
	static final int SHORT_LIST = 0xA0; // 1010 xxxx
	static final int SHORT_MAP = 0xB0; // 1011 xxxx
	static final int SHORT_STRUCT = 0xC0; // 110x xxxx

	static final int SHORT_MASK = 0xF0; // Match first four bits
	static final int SHORT_STRUCT_MASK = 0xE0; // Match first three bits
}
