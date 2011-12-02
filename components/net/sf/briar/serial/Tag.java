package net.sf.briar.serial;

interface Tag {

	static final byte FALSE = -1; // 1111 1111
	static final byte TRUE = -2; // 1111 1110
	static final byte INT8 = -3; // 1111 1101
	static final byte INT16 = -4; // 1111 1100
	static final byte INT32 = -5; // 1111 1011
	static final byte INT64 = -6; // 1111 1010
	static final byte FLOAT32 = -7; // 1111 1001
	static final byte FLOAT64 = -8; // 1111 1000
	static final byte STRING = -9; // 1111 0111
	static final byte BYTES = -10; // 1111 0110
	static final byte LIST = -11; // 1111 0101
	static final byte MAP = -12; // 1111 0100
	static final byte LIST_START = -13; // 1111 0011
	static final byte MAP_START = -14; // 1111 0010
	static final byte END = -15; // 1111 0001
	static final byte NULL = -16; // 1111 0000
	static final byte STRUCT = -17; // 1110 1111

	static final int SHORT_MASK = 0xF0; // Match first four bits
	static final int SHORT_STRING = 0x80; // 1000 xxxx
	static final int SHORT_BYTES = 0x90; // 1001 xxxx
	static final int SHORT_LIST = 0xA0; // 1010 xxxx
	static final int SHORT_MAP = 0xB0; // 1011 xxxx

	static final int SHORT_STRUCT_MASK = 0xE0; // Match first three bits
	static final int SHORT_STRUCT = 0xC0; // 110x xxxx
}
