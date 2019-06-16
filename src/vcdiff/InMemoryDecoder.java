package vcdiff;

import java.io.IOException;

class InMemoryDecoder {
  // Supported window indicator flags. At most one may be set.
  private static final int VCD_SOURCE = 1;
  private static final int VCD_TARGET = 2;

  /** Exception thrown when the target file size exceeds the maximum size. */
  public static class TargetSizeExceededException extends CodecException {
    private final int targetSize;

    private TargetSizeExceededException(int targetSize) {
      super("Target size too large.");
      this.targetSize = targetSize;
    }

    public int getTargetSize() {
      return targetSize;
    }
  }

  public static byte[] decode(byte[] dictBytes, byte[] deltaBytes, int maxTargetSize) throws CodecException {
    ByteRange deltaRange = new ByteRange(deltaBytes);
    parseHeader(deltaRange);

    // Scan the window sections to calculate the total target size before decoding.
    int targetSize = calculateTargetSize(
        new ByteRange(deltaBytes, deltaRange.position(), deltaRange.size()));
    if (targetSize > maxTargetSize) {
      throw new TargetSizeExceededException(targetSize);
    }

    byte[] targetBytes = new byte[targetSize];
    ByteRange targetRange = new ByteRange(targetBytes);

    // Actual decoding loop.
    while (!deltaRange.atEnd()) {
      decodeNextWindow(new ByteRange(dictBytes), deltaRange, targetRange);
    }
    if (!targetRange.atEnd()) {
      // This should be impossbible, since we've calculated the target size exactly above,
      // and if any window failed to decode entirely, then we should have thrown an error
      // earlier.
      throw new AssertionError("Target file was not fully decoded.");
    }
    return targetBytes;
  }

  private static void parseHeader(ByteRange headerRange) throws CodecException {
    if ((headerRange.getByte() & 0xff) != 0xd6 ||
        (headerRange.getByte() & 0xff) != 0xc3 ||
        (headerRange.getByte() & 0xff) != 0xc4) {
      throw new CodecException("Incorrect magic bytes");
    }
    if (headerRange.getByte() != 0) {
      throw new CodecException("Unsupported version byte");
    }
    if (headerRange.getByte() != 0) {
      throw new CodecException("Unsupported header indicator byte");
    }
  }

  private static int calculateTargetSize(ByteRange deltaRange) throws CodecException {
    int targetSize = 0;
    while (!deltaRange.atEnd()) {
      byte windowIndicator = deltaRange.getByte();
      if (windowIndicator != 0) {
        if (windowIndicator != VCD_SOURCE && windowIndicator != VCD_TARGET) {
          throw new CodecException("Invalid window indicator byte");
        }
        VarInt.readInt(deltaRange); // skip source segment size
        VarInt.readInt(deltaRange); // skip source segment position
      }
      ByteRange windowRange = deltaRange.getRange(VarInt.readInt(deltaRange));
      int windowTargetSize = VarInt.readInt(windowRange);
      if (Integer.MAX_VALUE - targetSize < windowTargetSize) {
        throw new CodecException("Total target size exceeds 31 bits");
      }
      targetSize += windowTargetSize;
    }
    return targetSize;
  }

  private static ByteRange decodeSourceSegment(ByteRange dictRange, ByteRange deltaRange, ByteRange targetRange)
      throws CodecException {
    byte windowIndicator = deltaRange.getByte();
    if (windowIndicator == 0) {
      return ByteRange.EMPTY;
    }
    ByteRange refRange;
    int refLen;
    if (windowIndicator == VCD_SOURCE) {
      refRange = dictRange;
      refLen = dictRange.size();
    } else if (windowIndicator == VCD_TARGET) {
      refRange = targetRange;
      refLen = targetRange.position();
    } else {
      throw new CodecException("Invalid window indicator byte");
    }
    int len = VarInt.readInt(deltaRange);
    int pos = VarInt.readInt(deltaRange);
    // This check suffices because len and pos must be nonnegative here.
    if (refLen - pos < len) {
      throw new CodecException("Source segment out of range");
    }
    return refRange.subRange(pos, pos + len);
  }

  private static void decodeNextWindow(ByteRange dictRange, ByteRange windowRange, ByteRange targetRange)
      throws CodecException {
    ByteRange sourceSegment = decodeSourceSegment(dictRange, windowRange, targetRange);
    ByteRange deltaEncodingRange = windowRange.getRange(VarInt.readInt(windowRange));
    ByteRange targetWindow = targetRange.getRange(VarInt.readInt(deltaEncodingRange));

    // Combined size of source + target segment must fit in an integer.
    if (Integer.MAX_VALUE - sourceSegment.size() < targetWindow.size()) {
      throw new CodecException("Combined size of source and target segments is too large.");
    }

    // Skip delta indicator byte, which should be 0. The spec allows this to
    // contain compression flags, but we don't support secondary compression.
    if (deltaEncodingRange.getByte() != 0) {
      throw new CodecException("Unsupported delta indicator byte");
    }

    int dataLen = VarInt.readInt(deltaEncodingRange);
    int instLen = VarInt.readInt(deltaEncodingRange);
    int addrLen = VarInt.readInt(deltaEncodingRange);
    ByteRange dataRange = deltaEncodingRange.getRange(dataLen);
    ByteRange instRange = deltaEncodingRange.getRange(instLen);
    ByteRange addrRange = deltaEncodingRange.getRange(addrLen);
    AddressCache addrCache = new AddressCache(addrRange);
    decodeInstructions(sourceSegment, targetWindow, dataRange, instRange, addrCache);
    if (!targetWindow.atEnd()) {
      throw new CodecException("Target window was not fully decoded");
    }
    // Compatibility note: we silentely ignore extra bytes at the end of
    // deltaEncodingRange,
    // dataRange, or addrRange.
  }

  private static int nextInstruction(ByteRange instRange, int lastInstruction) throws ByteRange.EndOfInputException {
    int nextInstruction = CodeTable.nextInstruction(lastInstruction);
    while (nextInstruction == 0 && !instRange.atEnd()) {
      nextInstruction = CodeTable.getInstructions(instRange.getByte() & 0xff);
    }
    return nextInstruction;
  }

  private static void decodeInstructions(ByteRange sourceSegment, ByteRange targetWindow, ByteRange dataRange,
      ByteRange instRange, AddressCache addrCache) throws CodecException {
    int instruction = 0;
    while ((instruction = nextInstruction(instRange, instruction)) != 0) {
      int type = CodeTable.instructionType(instruction);
      if (type == CodeTable.NOOP) {
        // This shoud never happen with the standard code table, but let's just continue.
        continue;
      }

      // All other instruction types take a size argument.
      int size = CodeTable.instructionSize(instruction);
      if (size == 0) {
        // No size encoded in the instruction. Read it separately.
        size = VarInt.readInt(instRange);
      }

      if (type == CodeTable.ADD) {
        while (size-- > 0) {
          targetWindow.putByte(dataRange.getByte());
        }
      } else if (type == CodeTable.RUN) {
        byte b = dataRange.getByte();
        while (size-- > 0) {
          targetWindow.putByte(b);
        }
      } else if (type == CodeTable.COPY) {
        int mode = CodeTable.instructionMode(instruction);
        int here = sourceSegment.size() + targetWindow.position();
        int addr = addrCache.decodeAddress(here, mode);
        // Compatibility note: the specification does not mention if addr == here is allowed
        // if size == 0. open-vcdiff assumes it's not, so we will do the same here.
        if (addr < sourceSegment.size()) {
          // Copy from source segment.
          //
          // Compatibility note: the open-vcdiff decoder supports copying across the
          // boundary of source segment and target window. However, this is explicitly
          // forbidden by the spec (see RFC 3284, Section 3. Delta Instructions: "We shall
          // that such a substring must be entirely contained in either S or T". So we do
          // not allow this here. Since the open-vcdiff encoder does not generate such
          // instructions itself, this should not cause any compatibility problems.
          if (size > sourceSegment.size() - addr) {
            throw new CodecException("Copy size exceeds source segment");
          }
          targetWindow.copyNonOverlapping(sourceSegment, addr, size);
        } else {
          // Copy from target window.
          targetWindow.copyFromThis(addr - sourceSegment.size(), size);
        }
      } else {
        throw new AssertionError("Unexpected instruction type");
      }
    }
  }

  private InMemoryDecoder() {
  }
}
