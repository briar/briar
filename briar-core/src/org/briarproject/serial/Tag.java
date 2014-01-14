package org.briarproject.serial;

interface Tag {

	byte FALSE = 0;
	byte TRUE = 1;
	byte INTEGER = 2;
	byte FLOAT = 3;
	byte STRING = 4;
	byte BYTES = 5;
	byte LIST = 6;
	byte MAP = 7;
	byte STRUCT = 8;
	byte END = 9;
	byte NULL = 10;
}
