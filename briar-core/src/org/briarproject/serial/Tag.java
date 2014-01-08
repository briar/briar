package org.briarproject.serial;

interface Tag {

	byte FALSE = (byte) 0xFF;
	byte TRUE = (byte) 0xFE;
	byte INT8 = (byte) 0xFD;
	byte INT16 = (byte) 0xFC;
	byte INT32 = (byte) 0xFB;
	byte INT64 = (byte) 0xFA;
	byte FLOAT32 = (byte) 0xF9;
	byte FLOAT64 = (byte) 0xF8;
	byte STRING = (byte) 0xF7;
	byte BYTES = (byte) 0xF6;
	byte LIST = (byte) 0xF5;
	byte MAP = (byte) 0xF4;
	byte STRUCT = (byte) 0xF3;
	byte END = (byte) 0xF2;
	byte NULL = (byte) 0xF1;
}
