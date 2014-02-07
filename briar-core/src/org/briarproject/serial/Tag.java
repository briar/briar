package org.briarproject.serial;

interface Tag {

	byte FALSE = 0x00;
	byte TRUE = 0x01;
	byte INTEGER_8 = 0x02;
	byte INTEGER_16 = 0x03;
	byte INTEGER_32 = 0x04;
	byte INTEGER_64 = 0x05;
	byte FLOAT = 0x06;
	byte STRING_8 = 0x07;
	byte STRING_16 = 0x08;
	byte STRING_32 = 0x09;
	byte BYTES_8 = 0x0A;
	byte BYTES_16 = 0x0B;
	byte BYTES_32 = 0x0C;
	byte LIST = 0x0D;
	byte MAP = 0x0E;
	byte STRUCT = 0x0F;
	byte END = 0x10;
	byte NULL = 0x11;
}
