package ch.verver.vcdiff;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class to generate VCDIFF test data.
 *
 * <p>This class is not thread-safe! Each instance can only be used from the thread that created it.
 */
public class TestDataGenerator {
  private static final byte[] HEADER = new byte[]{(byte) 0xD6, (byte) 0xC3, (byte) 0xC4, 0x00, 0x00};

  // 1 in 20 target windows uses no source segment (odds are relatively low, since this is not a very interesting case).
  private static final int NO_SOURCE_SEGMENT_ODDS = 20;

  // 1 in 50 instruction sizes are 0 (odds are relatively low, since an instruction with size 0 is not very interesting)
  private static final int SIZE_ZERO_ODDS = 20;

  // Minimum size of the dictionary. Also the minimum size of a source segment that will be used by the generator.
  // This exists mainly to avoid the "No viable entries in same-address cache!" edge case in generateDeltaEncoding().
  public final int MIN_DICT_SIZE = 100;

  private final int minDictSize;
  private final int maxDictSize;
  private final int minTargetSize;
  private final int maxTargetSize;
  private final int minNumWindows;
  private final int maxNumWindows;

  private ThreadLocalRandom rng;

  private byte[] dict;
  private byte[] delta;
  private byte[] target;

  // Address cache
  private final int nearSize = CodeTable.S_NEAR;
  private final int sameSize = CodeTable.S_SAME;
  private int nearAddrs[] = null;
  private int sameAddrs[] = null;
  private int nextSlot = -1;

  public TestDataGenerator(int minDictSize, int maxDictSize, int minTargetSize, int maxTargetSize, int minNumWindows, int maxNumWindows) {
    if (minDictSize < MIN_DICT_SIZE || maxDictSize < minDictSize || minTargetSize < 0 || maxTargetSize < minTargetSize || minNumWindows < 1 || maxNumWindows < minNumWindows) {
      throw new IllegalArgumentException();
    }
    this.minDictSize = minDictSize;
    this.maxDictSize = maxDictSize;
    this.minTargetSize = minTargetSize;
    this.maxTargetSize = maxTargetSize;
    this.minNumWindows = minNumWindows;
    this.maxNumWindows = maxNumWindows;
  }

  public byte[] getDictionary() {
    if (dict == null) {
      throw new IllegalStateException();
    }
    return dict;
  }

  public byte[] getDelta() {
    if (delta == null) {
      throw new IllegalStateException();
    }
    return delta;
  }

  public byte[] getTarget() {
    if (target == null) {
      throw new IllegalStateException();
    }
    return target;
  }

  public void generate() {
    rng = ThreadLocalRandom.current();

    int dictSize = rng.nextInt(minDictSize, maxDictSize + 1);
    int targetSize = rng.nextInt(minTargetSize, maxTargetSize + 1);
    int numWindows = rng.nextInt(minNumWindows, maxNumWindows + 1);

    dict = new byte[dictSize];
    rng.nextBytes(dict);

    ByteArrayOutputStream deltaStream = new ByteArrayOutputStream();
    writeBytes(deltaStream, HEADER);

    ByteArrayOutputStream targetStream = new ByteArrayOutputStream();
    for (int i = 0; i < numWindows; ++i) {
      int targetWindowSize = (targetSize - targetStream.size()) / (numWindows - i);
      // Vary target window size by 10% (except for the last window)
      if (i + 1 < numWindows) {
        targetWindowSize *= rng.nextDouble(0.9, 1.1);
      }
      ByteArrayOutputStream targetWindowStream = new ByteArrayOutputStream();
      generateTargetWindow(deltaStream, targetWindowSize, targetWindowStream);
      if (targetWindowStream.size() != targetWindowSize) {
        throw new AssertionError();
      }
      writeFromStream(targetStream, targetWindowStream);
    }
    delta = deltaStream.toByteArray();
    target = targetStream.toByteArray();
  }

  private void generateTargetWindow(
      ByteArrayOutputStream deltaStream, int targetWindowSize, ByteArrayOutputStream targetWindowStream) {
    byte[] srcWin = null;
    int srcLen = 0;
    int srcPos = 0;
    if (rng.nextInt(NO_SOURCE_SEGMENT_ODDS) == 0) {
      // Window indicator: 0
      deltaStream.write(0);
    } else if (targetWindowStream.size() < MIN_DICT_SIZE || rng.nextInt(2) == 0) {
      // Window indicator: 1 (VCD_SOURCE)
      deltaStream.write(1);
      srcWin = dict;
    } else {
      // Window indicator: 2 (VCD_TARGET)
      deltaStream.write(2);
      srcWin = targetWindowStream.toByteArray();
    }
    if (srcWin != null) {
      switch (rng.nextInt(4)) {
        case 0:
          srcLen = srcWin.length;
          srcPos = 0;
          break;
        case 1:
          srcLen = rng.nextInt(MIN_DICT_SIZE, srcWin.length);
          srcPos = 0;
          break;
        case 2:
          srcLen = rng.nextInt(MIN_DICT_SIZE, srcWin.length);
          srcPos = srcWin.length - srcLen;
          break;
        case 3:
          srcLen = rng.nextInt(MIN_DICT_SIZE, srcWin.length);
          srcPos = rng.nextInt(srcWin.length - srcLen);
          break;
        default:
          throw new AssertionError();
      }
      // Source segment length
      writeInt(deltaStream, srcLen);
      // Source segment position
      writeInt(deltaStream, srcPos);
    }

    // Reinitialize cache
    reinitializeAddressCache();

    // Generate delta encoding and corresponding target data.
    ByteArrayOutputStream deltaEncodingStream = new ByteArrayOutputStream();
    byte[] targetBytes = generateDeltaEncoding(srcWin, srcLen, srcPos, deltaEncodingStream, targetWindowSize);
    writeBytes(targetWindowStream, targetBytes);
    writeInt(deltaStream, deltaEncodingStream.size());
    writeFromStream(deltaStream, deltaEncodingStream);
  }

  private byte[] generateDeltaEncoding(
      byte[] srcWin, int srcLen, int srcPos,
      ByteArrayOutputStream deltaEncodingStream,
      int targetWindowSize) {
    int[] sameAddrIndices = new int[256];
    byte[] targetWindow = new byte[targetWindowSize];
    int targetWindowPos = 0;
    ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
    ByteArrayOutputStream instStream = new ByteArrayOutputStream();
    ByteArrayOutputStream addrStream = new ByteArrayOutputStream();

    // Length of the target window
    writeInt(deltaEncodingStream, targetWindowSize);

    // Delta indicator: 0 (no compression)
    deltaEncodingStream.write(0);

    while (targetWindowPos < targetWindowSize) {
      int code;
      int targetBytesLeft;
      do {
        // The logic here assumes we are using the standard code table.
        // The first 2 entries encode RUN and ADD instructions; the others
        // contain at least one COPY and therefore need either a source segment
        // or non-empty target window to copy from.
        code = rng.nextInt(srcWin == null && targetWindowPos == 0 ? 2 : 256);
        targetBytesLeft = targetWindowSize - targetWindowPos;
        // Check that fixed instruction sizes do not exceed bytes left in target window.
        for (int instr = CodeTable.getInstructions(code); instr != 0; instr = CodeTable.nextInstruction(instr)) {
          targetBytesLeft -= CodeTable.instructionSize(instr);
        }
      } while (targetBytesLeft < 0);
      instStream.write(code);
      for (int instr = CodeTable.getInstructions(code); instr != 0; instr = CodeTable.nextInstruction(instr)) {
        int instrSize = CodeTable.instructionSize(instr);
        if (instrSize == 0) {
          instrSize = generateSize(targetBytesLeft);
          writeInt(instStream, instrSize);
        }

        switch (CodeTable.instructionType(instr)) {
          case CodeTable.RUN:
            byte runByte = (byte) rng.nextInt(256);
            dataStream.write(runByte);
            for (int i = 0; i < instrSize; ++i) {
              targetWindow[targetWindowPos++] = runByte;
            }
            break;

          case CodeTable.ADD:
            byte[] addBytes = new byte[instrSize];
            rng.nextBytes(addBytes);
            writeBytes(dataStream, addBytes);
            for (int i = 0; i < instrSize; ++i) {
              targetWindow[targetWindowPos++] = addBytes[i];
            }
            break;

          case CodeTable.COPY:
            int mode = CodeTable.instructionMode(instr);
            int here = srcLen + targetWindowPos;
            int address;
            if (mode < 2) {  // VCD_SELF = 0, VCD_HERE = 1
              address = generateAddress(instrSize, 0, srcLen, targetWindowPos);
              writeInt(addrStream, mode == 0 ? address : here - address);
            } else if (mode - 2 < CodeTable.S_NEAR) {
              // Note: near address must be zero, or less than targetWindowPos.
              int nearAddr = nearAddrs[mode - 2];
              address = generateAddress(instrSize, nearAddr, srcLen, targetWindowPos);
              writeInt(addrStream, address - nearAddr);
            } else {
              // Note: same-addresses that are too close to srcLen are not viable, so we must exclude them here.
              int base = (mode - (CodeTable.S_NEAR + 2)) * 256;
              int numIndices = 0;
              for (int i = 0; i < 256; ++i) {
                int addr = sameAddrs[base + i];
                // Only consider sameAddrs that do not cause a copy across the source segment boundary.
                if (addr <= srcLen - instrSize || addr >= srcLen) {
                  sameAddrIndices[numIndices++] = i;
                }
              }
              if (numIndices == 0) {
                // This is possible, but should be unlikely if srcLen is sufficiently large.
                // We require the source segment is at least MIN_DICT_SIZE to make sure this doesn't happen in practice.
                throw new AssertionError("No viable entries in same-address cache!"
                    + " srcLen=" + srcLen);
              }
              int i = sameAddrIndices[rng.nextInt(numIndices)];
              addrStream.write(i);
              address = sameAddrs[base + i];
            }
            updateAddressCache(address);
            if (address < srcLen) {
              if (srcLen - address < instrSize) {
                throw new AssertionError("Copy crosses boundary between source segment and target window!"
                    + " mode=" + mode);
              }
              for (int i = 0; i < instrSize; ++i) {
                targetWindow[targetWindowPos++] = srcWin[srcPos + address + i];
              }
            } else {
              if (address - srcLen >= targetWindowPos) {
                throw new AssertionError("Copy starts outside initialized part of target window!"
                    + " mode=" + mode);
              }
              for (int i = 0; i < instrSize; ++i) {
                targetWindow[targetWindowPos++] = targetWindow[address - srcLen + i];
              }
            }
            break;

          default:
            throw new AssertionError();
        }
      }
    }
    if (targetWindowPos != targetWindowSize) {
      throw new AssertionError();
    }

    // Length of data for ADDs and RUNs
    writeInt(deltaEncodingStream, dataStream.size());

    // Length of instructions section
    writeInt(deltaEncodingStream, instStream.size());

    // Length of addresses for COPYs
    writeInt(deltaEncodingStream, addrStream.size());

    // Data section for ADDs and RUNs
    writeFromStream(deltaEncodingStream, dataStream);

    // Instructions and sizes section
    writeFromStream(deltaEncodingStream, instStream);

    // Addresses section for COPYs
    writeFromStream(deltaEncodingStream, addrStream);

    return targetWindow;
  }

  private static void writeBytes(ByteArrayOutputStream os, byte[] bytes) {
    // Before Java 11, the write(byte[]) overload is inherited from the base class and throws IOException.
    os.write(bytes, 0, bytes.length);
  }

  private static void writeFromStream(ByteArrayOutputStream destination, ByteArrayOutputStream source) {
    try {
      source.writeTo(destination);
    } catch (IOException e) {
      // Should be impossible, because ByteArrayOutputStream doesn't throw IOError.
      throw new AssertionError(e);
    }
  }

  private static void writeInt(ByteArrayOutputStream output, int i) {
    if (i < 0) {
      throw new IllegalArgumentException();
    }
    int shift = 0;
    while ((i >> shift) > 0x7f) {
      shift += 7;
    }
    while (shift > 0) {
      output.write(((i >> shift) & 0x7f) | 0x80);
      shift -= 7;
    }
    output.write(i & 0x7f);
  }

  private int generateSize(int maxSize) {
    if (maxSize < 0) {
      throw new IllegalArgumentException();
    }
    if (maxSize == 0 || rng.nextInt(SIZE_ZERO_ODDS) == 0) {
      return 0;
    }
    int size = rng.nextInt(Math.min(10, maxSize));
    int x = rng.nextInt();
    while (size < maxSize && (x & 1) == 1) {
      x >>= 1;
      size += Math.min(maxSize - size, 10);
    }
    return size;
  }

  private int generateAddress(int len, int minAddr, int srcLen, int targetWindowLen) {
    if (len < 0 || minAddr < 0 || srcLen < 0 || targetWindowLen < 0) {
      throw new IllegalArgumentException();
    }
    if (len == 0) {
      // This a bit weird corner case, so handle this separately.
      return rng.nextInt(minAddr, srcLen + targetWindowLen);
    }
    if (targetWindowLen == 0 || (srcLen - minAddr >= len && rng.nextInt(2) == 0)) {
      return generateSourceSegmentAddress(len, minAddr, srcLen);
    } else {
      return generateTargetWindowAddress(len, Math.max(0, minAddr - srcLen), targetWindowLen) + srcLen;
    }
  }

  private int generateSourceSegmentAddress(int len, int minPos, int srcLen) {
    if (minPos > srcLen - len) {
      throw new AssertionError();
    }
    int x = rng.nextInt(100);
    if (x < 5) {
      // 5% of cases: start of source segment
      return minPos;
    } else if (x < 10) {
      // 5% of cases: end of source segment
      return srcLen - len;
    } else {
      // 90% of cases: anywhere inside the source segment
      return rng.nextInt(minPos, srcLen - len + 1);
    }
  }

  private int generateTargetWindowAddress(int len, int minPos, int targetWindowLen) {
    if (minPos >= targetWindowLen) {
      throw new AssertionError();
    }
    int x = rng.nextInt(100);
    if (x < 5) {
      // 5% of cases: at the beginning of the target window
      return minPos;
    } else if (x < 35) {
      // 30% of cases: somewhere inside the target window
      return rng.nextInt(minPos, targetWindowLen);
    } else {
      // 65% of the cases: touching or overlapping the target window
      return rng.nextInt(Math.max(minPos, targetWindowLen - len), targetWindowLen);
    }
  }

  private void reinitializeAddressCache() {
    nearAddrs = new int[nearSize];
    sameAddrs = new int[sameSize * 256];
    nextSlot = 0;
  }

  private void updateAddressCache(int addr) {
    if (nearSize > 0) {
      nearAddrs[nextSlot] = addr;
      if (++nextSlot == nearSize) {
        nextSlot = 0;
      }
    }
    if (sameSize > 0) {
      sameAddrs[addr % (sameSize << 8)] = addr;
    }
  }
}
