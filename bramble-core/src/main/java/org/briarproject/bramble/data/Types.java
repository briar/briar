package org.briarproject.bramble.data;

interface Types {

	byte NULL = 0x00;
	byte FALSE = 0x10;
	byte TRUE = 0x11;
	byte INT_8 = 0x21;
	byte INT_16 = 0x22;
	byte INT_32 = 0x24;
	byte INT_64 = 0x28;
	byte FLOAT_64 = 0x38;
	byte STRING_8 = 0x41;
	byte STRING_16 = 0x42;
	byte STRING_32 = 0x44;
	byte RAW_8 = 0x51;
	byte RAW_16 = 0x52;
	byte RAW_32 = 0x54;
	byte LIST = 0x60;
	byte DICTIONARY = 0x70;
	byte END = (byte) 0x80;
}
