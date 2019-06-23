#!/usr/bin/env python3

import sys

VCD_SOURCE = 1
VCD_TARGET = 2

def IntEncodedSize(i):
    if i == 0:
        return 1
    assert i > 0
    n = 0
    while i > 0:
        i >>= 7
        n += 1
    return n

def ReadInt(f):
    last_byte = 0x80
    result = 0
    while last_byte & 0x80:
        data = f.read(1)
        if not data:
            return None
        last_byte = data[0]
        result = (result << 7) + (last_byte & 0x7f)
    return result

def Parse(filename):
    # Minimum size of the dictionary (input) file necessary to decode the delta.
    min_dictionary_size = 0
    # Size of the target (output) file after decoding the delta.
    target_size = 0
    # Number of window sections in the delta file.
    window_count = 0
    # Maximum size of a target segment in any delta window.
    window_target_size_max = 0
    # Maximum size of the delta encoding in any window.
    window_delta_size_max = 0
    # Statistics for dictionary source segments.
    dictionary_source_count = 0
    dictionary_source_size_sum = 0
    dictionary_source_size_max = 0
    # Statisics for target source segments.
    target_source_count = 0
    target_source_size_sum = 0
    target_source_size_max = 0

    with open(filename, 'rb') as f:
        data = f.read(5)
        if len(data) < 5:
            return 'Truncated header section'
        if data[0:3] != b'\xD6\xC3\xC4':
            return 'Invalid header magic'
        if data[3] != 0:
            return 'Unsupported header version'
        if data[4] != 0:
            # Standard defines: VCD_DECOMPRESS, VCD_CODETABLE
            return 'Unsupported header flags'
        while True:
            data = f.read(1)
            if not data:
                break  # end of file
            source = data[0]
            if source not in (0, VCD_SOURCE, VCD_TARGET):
                return 'Unsupported window flags'
            if source != 0:
                size = ReadInt(f)
                if size is None:
                    return 'Could not read window source length'
                begin = ReadInt(f)
                if begin is None:
                    return 'Could not read window source offset'
                end = begin + size
                if source == VCD_SOURCE:
                    min_dictionary_size = max(min_dictionary_size, end)
                    dictionary_source_count += 1
                    dictionary_source_size_sum += size
                    dictionary_source_size_max = max(dictionary_source_size_max, size)
                else:
                    assert source == VCD_TARGET
                    if end > target_size:
                        return 'Target segment out of range'
                    target_source_count += 1
                    target_source_size_sum += size
                    target_source_size_max = max(target_source_size_max, size)
            window_delta_size = ReadInt(f)
            if window_delta_size is None:
                return 'Could not read window delta encoding size'
            window_target_size = ReadInt(f)
            if window_target_size is None:
                return 'Could not read window target size'
            data = f.read(1)
            if data[0] != 0:
                # Standard defines: VCD_DATACOMP, VCD_INSTCOMP, VCD_ADDRCOMP
                return 'Unsupported delta flags'

            remaining_length = window_delta_size - IntEncodedSize(window_target_size) - 1
            # This is not very memory-efficient; the alternative is to use f.seek(),
            # but that does fail when seeking past EOF, so this is more robust in
            # that it allows us to detect truncated windows.
            if len(f.read(remaining_length)) != remaining_length:
                return 'Truncated window section'

            target_size += window_target_size
            window_count += 1
            window_target_size_max = max(window_target_size_max, window_target_size)
            window_delta_size_max = max(window_delta_size_max, window_delta_size)

        # Print stats.
        print('min_dictionary_size =', min_dictionary_size)
        print('target_size =', target_size)
        print('window_count =', window_count)
        print('window_target_size_max =', window_target_size_max)
        print('window_delta_size_max =', window_delta_size_max)
        print('dictionary_source_count =', dictionary_source_count)
        print('dictionary_source_size_sum =', dictionary_source_size_sum)
        print('dictionary_source_size_max =', dictionary_source_size_max)
        print('target_source_count =', target_source_count)
        print('target_source_size_sum =', target_source_size_sum)
        print('target_source_size_max =', target_source_size_max)

        return None

error = Parse(sys.argv[1])
if error:
    print('Parse failed:', error)
    sys.exit(1)

