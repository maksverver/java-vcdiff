package ch.verver.vcdiff;

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
    ByteViewReader deltaSection = new ByteViewReader(new ByteView(deltaBytes));
    parseHeader(deltaSection);

    // Scan the window sections to calculate the total target size before decoding.
    int targetSize = calculateTargetSize(new ByteViewReader(deltaSection));
    if (targetSize > maxTargetSize) {
      throw new TargetSizeExceededException(targetSize);
    }

    // Actual decoding loop.
    ByteView dictView = new ByteView(dictBytes);
    byte[] targetBytes = new byte[targetSize];
    ByteWriter targetView = new ByteWriter(targetBytes);
    while (!deltaSection.atEnd()) {
      decodeNextWindow(dictView, deltaSection, targetView);
    }
    if (!targetView.atEnd()) {
      // This should be impossbible, since we've calculated the target size exactly above,
      // and if any window failed to decode entirely, then we should have thrown an error
      // earlier.
      throw new AssertionError("Target file was not fully decoded.");
    }
    return targetBytes;
  }

  private static void parseHeader(ByteViewReader headerSection) throws CodecException {
    if ((headerSection.readByte() & 0xff) != 0xd6 ||
        (headerSection.readByte() & 0xff) != 0xc3 ||
        (headerSection.readByte() & 0xff) != 0xc4) {
      throw new CodecException("Incorrect magic bytes");
    }
    if (headerSection.readByte() != 0) {
      throw new CodecException("Unsupported version byte");
    }
    if (headerSection.readByte() != 0) {
      throw new CodecException("Unsupported header indicator byte");
    }
  }

  private static int calculateTargetSize(ByteViewReader deltaSection) throws CodecException {
    int targetSize = 0;
    while (!deltaSection.atEnd()) {
      byte windowIndicator = deltaSection.readByte();
      if (windowIndicator != 0) {
        if (windowIndicator != VCD_SOURCE && windowIndicator != VCD_TARGET) {
          throw new CodecException("Invalid window indicator byte");
        }
        VarInt.readInt(deltaSection); // skip source segment size
        VarInt.readInt(deltaSection); // skip source segment position
      }
      ByteViewReader window = deltaSection.newSubReader(VarInt.readInt(deltaSection));
      int windowTargetSize = VarInt.readInt(window);
      if (Integer.MAX_VALUE - targetSize < windowTargetSize) {
        throw new CodecException("Total target size exceeds 31 bits");
      }
      targetSize += windowTargetSize;
    }
    return targetSize;
  }

  private static ByteView decodeSourceSegment(ByteView dict, ByteWriter target, ByteViewReader window)
      throws CodecException {
    byte windowIndicator = window.readByte();
    if (windowIndicator == 0) {
      return ByteView.EMPTY;
    }
    ByteView source;
    if (windowIndicator == VCD_SOURCE) {
      source = dict;
    } else if (windowIndicator == VCD_TARGET) {
      source = target.asByteView();
    } else {
      throw new CodecException("Invalid window indicator byte");
    }
    int len = VarInt.readInt(window);
    int pos = VarInt.readInt(window);
    // This check suffices because len and pos must be nonnegative here.
    if (source.size() - pos < len) {
      throw new CodecException("Source segment out of range");
    }
    return source.subView(pos, pos + len);
  }

  private static void decodeNextWindow(ByteView dict, ByteViewReader deltaWindow, ByteWriter target)
      throws CodecException {
    ByteView sourceSegment = decodeSourceSegment(dict, target, deltaWindow);
    ByteViewReader deltaEncoding = deltaWindow.newSubReader(VarInt.readInt(deltaWindow));
    ByteWriter targetWindow = target.newSubWriter(VarInt.readInt(deltaEncoding));

    // Combined size of source + target segment must fit in an integer.
    if (Integer.MAX_VALUE - sourceSegment.size() < targetWindow.size()) {
      throw new CodecException("Combined size of source and target segments is too large.");
    }

    // Skip delta indicator byte, which should be 0. The spec allows this to
    // contain compression flags, but we don't support secondary compression.
    if (deltaEncoding.readByte() != 0) {
      throw new CodecException("Unsupported delta indicator byte");
    }

    int dataLen = VarInt.readInt(deltaEncoding);
    int instLen = VarInt.readInt(deltaEncoding);
    int addrLen = VarInt.readInt(deltaEncoding);
    ByteViewReader data = deltaEncoding.newSubReader(dataLen);
    ByteViewReader inst = deltaEncoding.newSubReader(instLen);
    ByteViewReader addr = deltaEncoding.newSubReader(addrLen);
    AddressCache addrCache = new AddressCache(addr);
    decodeInstructions(sourceSegment, targetWindow, data, inst, addrCache);
    if (!targetWindow.atEnd()) {
      throw new CodecException("Target window was not fully decoded");
    }
    // Compatibility note: we silentely ignore extra bytes at the end of
    // deltaEncoding, dataRange, and addrRange.
  }

  private static int nextInstruction(ByteViewReader instRange, int lastInstruction)
      throws ByteViewReader.EndOfInputException {
    int nextInstruction = CodeTable.nextInstruction(lastInstruction);
    while (nextInstruction == 0 && !instRange.atEnd()) {
      nextInstruction = CodeTable.getInstructions(instRange.readByte() & 0xff);
    }
    return nextInstruction;
  }

  private static void decodeInstructions(
      ByteView sourceSegment, ByteWriter targetWindow,
      ByteViewReader dataRange, ByteViewReader instRange,
      AddressCache addrCache) throws CodecException {
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
        targetWindow.copyFrom(dataRange, size);
      } else if (type == CodeTable.RUN) {
        byte b = dataRange.readByte();
        targetWindow.writeRun(b, size);
      } else if (type == CodeTable.COPY) {
        int mode = CodeTable.instructionMode(instruction);
        int here = sourceSegment.size() + targetWindow.position();
        // Compatibility note: the specification does not mention if addr == here is allowed
        // if size == 0. open-vcdiff assumes it's not, so we will do the same here.
        int addr = addrCache.decodeAddress(here, mode);
        if (addr < sourceSegment.size()) {
          // Copy from source segment.
          //
          // Compatibility note: the open-vcdiff decoder supports copying across the
          // boundary of source segment and target window. However, this is explicitly
          // forbidden by the spec (see RFC 3284, Section 3. Delta Instructions: "We shall
          // assert that such a substring must be entirely contained in either S or T".
          // So we do not allow this here. Since the open-vcdiff encoder does not generate
          // such instructions itself, this should not cause any compatibility problems.
          if (size > sourceSegment.size() - addr) {
            throw new CodecException("Copy size exceeds source segment");
          }
          targetWindow.copyFrom(sourceSegment, addr, size);
        } else {
          // Copy from target window.
          targetWindow.copyFromThis(addr - sourceSegment.size(), size);
        }
      } else {
        throw new AssertionError("Unexpected instruction type");
      }
    }
  }

  private InMemoryDecoder() {}
}
