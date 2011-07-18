package net.sf.briar.api.serial;

public interface Tag {

	public static final byte FALSE = -1; // 1111 1111
	public static final byte TRUE = -2; // 1111 1110
	public static final byte INT8 = -3; // 1111 1101
	public static final byte INT16 = -4; // 1111 1100
	public static final byte INT32 = -5; // 1111 1011
	public static final byte INT64 = -6; // 1111 1010
	public static final byte FLOAT32 = -7; // 1111 1001
	public static final byte FLOAT64 = -8; // 1111 1000
	public static final byte STRING = -9; // 1111 0111
	public static final byte RAW = -10; // 1111 0110
	public static final byte LIST = -11; // 1111 0101
	public static final byte MAP = -12; // 1111 0100
	public static final byte LIST_START = -13; // 1111 0011
	public static final byte MAP_START = -14; // 1111 0010
	public static final byte END = -15; // 1111 0001
	public static final byte NULL = -16; // 1111 0000

	public static final int SHORT_MASK = 0xF0; // Match first four bits
	public static final int SHORT_STRING = 0x80; // 1000 xxxx
	public static final int SHORT_RAW = 0x90; // 1001 xxxx
	public static final int SHORT_LIST = 0xA0; // 1010 xxxx
	public static final int SHORT_MAP = 0xB0; // 1011 xxxx
	public static final int USER_MASK = 0xE0; // Match first three bits
	public static final int USER = 0xC0; // 110x xxxx
	public static final byte USER_EXT = -32; // 1110 0000
}
