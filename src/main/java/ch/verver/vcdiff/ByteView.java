package ch.verver.vcdiff;

/** A read-only view of a substring of a byte array. */
class ByteView {
  public static final ByteView EMPTY = new ByteView(new byte[0], 0, 0);

  // Invariants:
  //
  //   0 <= offset <= length
  //   offset + length <= data.length
  private final byte[] data;
  private final int offset, length;

  protected ByteView(ByteView original) {
    this.data = original.data;
    this.offset = original.offset;
    this.length = original.length;
  }

  ByteView(byte[] data) {
    this(data, 0, data.length);
  }

  ByteView(byte[] data, int offset, int length) {
    if (offset < 0 || length < 0 || length > data.length - offset) {
      throw new IndexOutOfBoundsException();
    }
    this.data = data;
    this.offset = offset;
    this.length = length;
  }

  byte get(int index) {
    // To improve performance, we could skip bounds checking here.
    checkBounds(index, 1);
    return data[offset + index];
  }

  int size() {
    return length;
  }

  ByteView subView(int pos, int len) {
    checkBounds(pos, len);
    return new ByteView(data, offset + pos, len);
  }

  void copyTo(int pos, byte[] out, int outPos, int len) {
    // To improve performance, we could skip bounds checking here.
    checkBounds(pos, len);
    System.arraycopy(data, offset + pos, out, outPos, len);
  }

  private void checkBounds(int pos, int len) {
    if (pos < 0 || len < 0 || len > length - pos) {
      throw new IndexOutOfBoundsException();
    }
  }
}
