package ch.verver.vcdiff;

import java.util.Arrays;

class ByteWriter {
  public static class EndOfOutputException extends CodecException {
    EndOfOutputException() {
      super("Premature end of output buffer");
    }
  }

  // Same invariants as ByteView.
  private final byte[] data;
  private final int offset, length;

  // Write position in the buffer; relative to offset.
  // Invariant: 0 <= pos <= length
  private int pos = 0;

  ByteWriter(byte[] data) {
    this(data, 0, data.length);
  }

  ByteWriter(byte[] data, int offset, int length) {
    if (offset < 0 || length < 0 || length > data.length - offset) {
      throw new IndexOutOfBoundsException();
    }
    this.data = data;
    this.offset = offset;
    this.length = length;
  }

  int position() {
    return pos;
  }

  int size() {
    return length;
  }

  boolean atEnd() {
    return pos >= length;
  }

  ByteView asByteView() {
    return new ByteView(data, offset, pos);
  }

  ByteWriter newSubWriter(int length) throws EndOfOutputException {
    return new ByteWriter(data, offset + reserveCapacity(length), length);
  }

  void writeRun(byte b, int len) throws EndOfOutputException {
    Arrays.fill(data, offset + reserveCapacity(len), offset + pos, b);
  }

  void copyFrom(ByteViewReader input, int len) throws ByteViewReader.EndOfInputException, EndOfOutputException {
    input.copyTo(data, offset + reserveCapacity(len), len);
  }

  void copyFrom(ByteView bytes, int pos, int len) throws EndOfOutputException {
    bytes.copyTo(pos, data, offset + reserveCapacity(len), len);
  }

  void copyFromThis(int pos, int len) throws EndOfOutputException {
    if (pos < 0 || pos >= this.pos) {
      throw new IndexOutOfBoundsException(pos);
    }
    int i = offset + pos;
    int j = offset + reserveCapacity(len);
    for (int n; (n = (j - i)) < len; j += n) {
      System.arraycopy(data, i, data, j, n);
      len -= n;
    }
    System.arraycopy(data, i, data, j, len);
  }

  private int reserveCapacity(int n) throws EndOfOutputException {
    if (n < 0) {
      throw new IllegalArgumentException();
    }
    if (length - pos < n) {
      throw new EndOfOutputException();
    }
    int oldPos = pos;
    pos += n;
    return oldPos;
  }
}
