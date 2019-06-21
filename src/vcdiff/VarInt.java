package vcdiff;

/**
 * Utility class for decoding variable length integers.
 *
 * <p>From the <a href="https://www.ietf.org/rfc/rfc3284.txt">VCDIFF spec</a>
 * section 2:
 *
 * <blockquote>
 * Vcdiff encodes unsigned integer values using a portable, variable-
 * sized format (originally introduced in the Sfio library [7]).  This
 * encoding treats an integer as a number in base 128.  Then, each digit
 * in this representation is encoded in the lower seven bits of a byte.
 * Except for the least significant byte, other bytes have their most
 * significant bit turned on to indicate that there are still more
 * digits in the encoding.
 * </blockquote>
 *
 * <p>The specification does not say whether the variable-length encoding may
 * contain leading zeroes. For example, the number 42 can be encoded in
 * 100 bytes: [128 128 128 128 .. 42]. This implementation will remove leading
 * zeroes and correctly decode this as 42. As a consequence, reading an integer
 * may take time proportional to the size of the input, and consume arbitrarily
 * many bytes of input.
 */
class VarInt {

  /** Exception thrown when an encoded integer is too large. */
  static class TooLargeException extends CodecException {
    TooLargeException(String message) {
      super(message);
    }
  }

  /** Reads a variable-length encoded nonnegative integer as a 31-bit int. */
  public static int readInt(ByteViewReader input)
      throws ByteViewReader.EndOfInputException, TooLargeException {
    byte b = input.readByte();
    int i = b & 0x7f;
    while ((b & 0x80) != 0) {
      if (i > (Integer.MAX_VALUE >> 7)) {
        throw new TooLargeException("Integer value exceeds 31 bits");
      }
      b = input.readByte();
      i = (i << 7) | (b & 0x7f);
    }
    return i;
  }

  // We could trivially add a method readLong() to read 63-bit integers.
  // However, we don't need it because we don't support sizes larger than 31-bits.

  private VarInt() {}
}