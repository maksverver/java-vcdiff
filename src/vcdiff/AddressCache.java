package vcdiff;

/**
 * Implements the VCDIFF address cache as defined in RFC 3284 section 5.1:
 * Address Encoding Modes of COPY Instructions.
 *
 * <p>Since application-defined code tables are not suppored, only the standard
 * values of S_NEAR and S_SAME need to be supported, though the implementation
 * here is general enough that it could easily support custom parameters.
 */
class AddressCache {
  private final int nearSize;
  private final int sameSize;
  private final int nearAddrs[];
  private final int sameAddrs[];
  private final ByteViewReader addresses;
  private int nextSlot = 0;

  AddressCache(ByteViewReader addresses) {
    // Initialize cache using the standard code table parameters.
    nearSize = CodeTable.S_NEAR;
    sameSize = CodeTable.S_SAME;
    nearAddrs = new int[nearSize];
    sameAddrs = new int[sameSize * 256];
    this.addresses = addresses;
  }

  /**
   * Decodes an address and updates the cache accordingly.
   *
   * @return the decoded address, between 0 and {@code here} (exclusive)
   * @throws CodecException if the decoded address was out of range, or the
   *    given mode was invalid
   */
  public int decodeAddress(int here, int mode) throws CodecException {
    int address = onlyDecodeAddress(here, mode);
    if (address < 0 || address >= here) {
      throw new CodecException("Decoded address out of range");
    }
    updateCache(address);
    return address;
  }

  private int onlyDecodeAddress(int here, int mode) throws CodecException {
    if (mode == 0) { // VCD_SELF
      return VarInt.readInt(addresses);
    }
    if (mode == 1) { // VCD_HERE
      return here - VarInt.readInt(addresses);
    }
    if (mode < 2) {
      throw new CodecException("Invalid mode");
    }
    int m = mode - 2;
    if (m < nearSize) {
      // Since both addends must be positive, if the sum overflows, the result
      // must be negative, which we will detect in decodeAddress() above.
      return nearAddrs[m] + VarInt.readInt(addresses);
    }
    m -= nearSize;
    if (m < sameSize) {
      return sameAddrs[(m << 8) | (addresses.readByte() & 0xff)];
    }
    throw new CodecException("Invalid mode");
  }

  private void updateCache(int addr) {
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
