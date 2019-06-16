package vcdiff;

/**
 * Implements the default VCDIFF code table as defined in RFC 3284 section 5.6:
 * The Code Table. Application-defined code tables are not supported.
 */
class CodeTable {
  // Instruction types. Values are defined in the spec.
  public static final int NOOP = 0;
  public static final int ADD = 1;
  public static final int RUN = 2;
  public static final int COPY = 3;

  // Default near/same cache sizes. Defined in the spec.
  public static int S_NEAR = 4;
  public static int S_SAME = 3;

  // A single instruction is an 11-bit field, with a 2-bit type, 5-bit size,
  // and a 4-bit mode, encoded in little-endian. Two instructions may be
  // combined into a single 22-bit integer (the first instruction in the lower
  // 11 bits).
  //
  //   11 10  9  8  7  6  5  4  3  2  1  0
  //  ---+--+--+--+--+--+--+--+--+--+--+---+
  //     |  mode   |     size       | type |
  //  ---+--+--+--+--+--+--+--+--+--+--+---+
  //
  // Note that a NOOP instruction is encoded as 0, so a single instruction is
  // equal to a combined instruction where the second instruction is a NOOP.
  private static final int[] ENTRIES = generateDefaultCodeTable();

  /**
   * Retrieves the (combined) instructions for the given code (between 0 and
   * 255, inclusive), or throws an IndexOutOfBoundsException.
   *
   * @throws IndexOutOfBoundsException if code is less than 0 or greater than 255
   */
  static int getInstructions(int code) {
    return ENTRIES[code];
  }

  /** Retrieves the instruction type (between 0 and 3, inclusive). */
  static int instructionType(int i) {
    return (i & 0b00000000011) >> 0;
  }

  /** Retrieves the instruction size (a nonnegative integer). */
  static int instructionSize(int i) {
    return (i & 0b00001111100) >> 2;
  }

  /** Retrieves the instruction mode (between 0 and 8, inclusive). */
  static int instructionMode(int i) {
    return (i & 0b11110000000) >> 7;
  }

  /**
   * Returns the next instruction in a combined instruction, or 0 if there are
   * no more instructions (i.e., all following instructions are NOOPs).
   */
  static int nextInstruction(int i) {
    return (i >>> 11);
  }

  private static int encodeInstruction(int type, int size, int mode) {
    return (type << 0) | (size << 2) | (mode << 7);
  }

  private static int combineInstructions(int first, int second) {
    return first | (second << 11);
  }

  private static int[] generateDefaultCodeTable() {
    int[] result = new int[256];
    int pos = 0;
    // The contents of the code table generated here are defined in RFC 3284
    // section 5.6: The Code Table.
    result[pos++] = encodeInstruction(RUN, 0, 0);
    for (int size = 0; size <= 17; ++size) {
      result[pos++] = encodeInstruction(ADD, size, 0);
    }
    for (int mode = 0; mode <= 8; ++mode) {
      result[pos++] = encodeInstruction(COPY, 0, mode);
      for (int size = 4; size <= 18; ++size) {
        result[pos++] = encodeInstruction(COPY, size, mode);
      }
    }
    for (int mode = 0; mode <= 8; ++mode) {
      for (int addSize = 1; addSize <= 4; ++addSize) {
        int maxCopySize = mode < 6 ? 6 : 4;
        for (int copySize = 4; copySize <= maxCopySize; ++copySize) {
          result[pos++] = combineInstructions(
            encodeInstruction(ADD, addSize, 0),
            encodeInstruction(COPY, copySize, mode));
        }
      }
    }
    for (int mode = 0; mode <= 8; ++mode) {
      result[pos++] = combineInstructions(
        encodeInstruction(COPY, 4, mode),
        encodeInstruction(ADD, 1, 0));
    }
    if (pos != result.length) {
      throw new AssertionError();
    }
    return result;
  }
}