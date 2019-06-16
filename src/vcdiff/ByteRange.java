package vcdiff;

/** A range of a byte array. */
class ByteRange {
  static final ByteRange EMPTY = new ByteRange(new byte[0]);

  public static class EndOfInputException extends CodecException {
    EndOfInputException() {
      super("Premature end of input buffer");
    }
  }

  public static class EndOfOutputException extends CodecException {
    EndOfOutputException() {
      super("Premature end of output buffer");
    }
  }

  private final byte[] data;
  private final int begin, end;
  private int pos;

  ByteRange(byte[] data) {
    this(data, 0);
  }

  ByteRange(byte[] data, int begin) {
    this(data, begin, data.length);
  }

  ByteRange(byte[] data, int begin, int end) {
    if (begin < 0) {
      throw new IndexOutOfBoundsException(begin);
    }
    if (end < begin || end > data.length) {
      throw new IndexOutOfBoundsException(end);
    }
    this.data = data;
    this.begin = begin;
    this.end = end;
    this.pos = begin;
  }

  int size() {
    return end - begin;
  }

  int position() {
    return pos - begin;
  }

  boolean atEnd() {
    return pos >= end;
  }

  byte getByte() throws EndOfInputException {
    if (pos >= end) {
      throw new EndOfInputException();
    }
    return data[pos++];
  }

  ByteRange subRange(int begin, int end) {
    int thisSize = this.end - this.begin;
    if (begin < 0) {
      throw new IndexOutOfBoundsException(begin);
    }
    if (end < begin || end > thisSize) {
      throw new IndexOutOfBoundsException(end);
    }
    return new ByteRange(data, this.begin + begin, this.begin + end);
  }

  ByteRange getRange(int size) throws EndOfInputException {
    if (end - pos < size) {
      throw new EndOfInputException();
    }
    int oldPos = pos;
    pos += size;
    return new ByteRange(data, oldPos, pos);
  }

  void putByte(byte b) throws EndOfOutputException {
    if (pos >= end) {
      throw new EndOfOutputException();
    }
    data[pos++] = b;
  }

  void copyNonOverlapping(ByteRange source, int begin, int size) throws EndOfOutputException {
    int sourceSize = source.data == this.data ? pos - begin : source.end - source.begin;
    if (begin < 0 || begin > sourceSize) {
      throw new IndexOutOfBoundsException(end);
    }
    if (size < 0 || size > sourceSize - begin) {
      throw new IndexOutOfBoundsException();
    }
    int i = source.begin + begin;
    while (size-- > 0) {
      putByte(source.data[i++]);
    }
  }

  // Note that this method must explicitly accept overlapping copies!
  void copyFromThis(int begin, int size) throws EndOfOutputException {
    if (begin < 0 || begin >= pos) {
      throw new IndexOutOfBoundsException(begin);
    }
    if (size < 0) {
      throw new IndexOutOfBoundsException();
    }
    int i = this.begin + begin;
    while (size-- > 0) {
      putByte(data[i++]);
    }
  }
}