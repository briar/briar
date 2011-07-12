package net.sf.briar.api.serial;

public interface Tag {

	public static final byte FALSE = -1, TRUE = -2;
	public static final byte INT8 = -3, INT16 = -4, INT32 = -5, INT64 = -6;
	public static final byte FLOAT32 = -7, FLOAT64 = -8;
	public static final byte UTF8 = -9, RAW = -10;
	public static final byte LIST_DEF = -11, MAP_DEF = -12;
	public static final byte LIST_INDEF = -13, MAP_INDEF = -14, END = -15;
	public static final byte NULL = -16;
}
