package ch.verver.vcdiff;

/** Base exception for all errors that happen during encoding/decoding. */
public class CodecException extends Exception {
  CodecException(String message) {
    super(message);
  }
}
