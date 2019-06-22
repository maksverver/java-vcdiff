package ch.verver.vcdiff;

class ByteViewReader {
  /**
   * Exception thrown when an attempt is made to read past the end of the input.
   *
   * <p>Afterwards, the position of the reader is undefined.
   */
  static class EndOfInputException extends CodecException {
    EndOfInputException() {
      super("Premature end of input buffer");
    }
  }

  private final ByteView byteView;

  // Read position in the buffer; relative to offset.
  // Invariant: 0 <= pos <= byteView.size()
  private int pos = 0;

  ByteViewReader(ByteView byteView) {
    this.byteView = byteView;
  }

  int position() {
    return pos;
  }

  boolean atEnd() {
    return pos >= byteView.size();
  }

  byte readByte() throws EndOfInputException {
    return byteView.get(reserveCapacity(1));
  }

  ByteViewReader newSubReader(int len) throws EndOfInputException {
    return new ByteViewReader(byteView.subView(reserveCapacity(len), len));
  }

  void copyTo(byte[] out, int pos, int len) throws EndOfInputException {
    byteView.copyTo(reserveCapacity(len), out, pos, len);
  }

  private int reserveCapacity(int n) throws EndOfInputException {
    if (n < 0) {
      throw new IllegalArgumentException();
    }
    if (byteView.size() - pos < n) {
      throw new EndOfInputException();
    }
    int oldPos = pos;
    pos += n;
    return oldPos;
  }
}
