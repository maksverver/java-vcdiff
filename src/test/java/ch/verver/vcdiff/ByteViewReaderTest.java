package ch.verver.vcdiff;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Assert;
import org.junit.Test;

public class ByteViewReaderTest {
  @Test
  public void readByte() throws Exception {
    ByteViewReader r = new ByteViewReader(new ByteView(new byte[] { 10, 11, 12 }));
    assertThat(r.atEnd()).isFalse();
    assertThat(r.position()).isEqualTo(0);
    assertThat(r.readByte()).isEqualTo(10);

    assertThat(r.atEnd()).isFalse();
    assertThat(r.position()).isEqualTo(1);
    assertThat(r.readByte()).isEqualTo(11);

    assertThat(r.atEnd()).isFalse();
    assertThat(r.position()).isEqualTo(2);
    assertThat(r.readByte()).isEqualTo(12);

    assertThat(r.atEnd()).isTrue();
    assertThat(r.position()).isEqualTo(3);
    assertReadByteThrowsEndOfInputException(r);
    assertThat(r.position()).isEqualTo(3);
  }

  @Test
  public void newSubReader() throws Exception {
    ByteViewReader r = new ByteViewReader(new ByteView(new byte[] { 10, 11, 12, 13, 14, 15 }));
    ByteViewReader s1 = r.newSubReader(2);
    ByteViewReader s2 = r.newSubReader(2);
    assertThat(r.position()).isEqualTo(4);
    assertThat(s1.position()).isEqualTo(0);
    assertThat(s2.position()).isEqualTo(0);

    assertThat(r.readByte()).isEqualTo(14);

    assertThat(s1.readByte()).isEqualTo(10);
    assertThat(s2.readByte()).isEqualTo(12);
    assertThat(s1.readByte()).isEqualTo(11);
    assertThat(s2.readByte()).isEqualTo(13);

    assertThat(s1.atEnd()).isTrue();
    assertReadByteThrowsEndOfInputException(s1);
    assertThat(s2.atEnd()).isTrue();
    assertReadByteThrowsEndOfInputException(s2);

    assertThat(r.atEnd()).isFalse();  // 1 byte left
    assertNewSubReaderThrowsEndOfInputException(r, 2);
  }

  @Test
  public void copyTo() throws Exception {
    ByteViewReader r = new ByteViewReader(new ByteView(new byte[]{1, 2, 3, 4, 5}));
    byte[] out = new byte[5];
    r.copyTo(out, 3, 2);
    r.copyTo(out, 0, 3);
    assertThat(r.atEnd()).isTrue();
    r.copyTo(out, 5, 0);
    assertThat(out).isEqualTo(new byte[]{3, 4, 5, 1, 2});

    assertCopyToThrowsEndOfInputException(r, out, 0, 1);
  }

  private static void assertReadByteThrowsEndOfInputException(ByteViewReader r) {
    try {
      r.readByte();
      Assert.fail("Expected EndOfInputException to be thrown");
    } catch (ByteViewReader.EndOfInputException unused) {
      // expected
    }
  }

  private static void assertNewSubReaderThrowsEndOfInputException(ByteViewReader r, int len) {
    try {
      r.newSubReader(len);
      Assert.fail("Expected EndOfInputException to be thrown");
    } catch (ByteViewReader.EndOfInputException unused) {
      // expected
    }
  }

  private static void assertCopyToThrowsEndOfInputException(ByteViewReader r, byte[] out, int pos, int len) {
    try {
      r.copyTo(out, pos, len);
      Assert.fail("Expected EndOfInputException to be thrown");
    } catch (ByteViewReader.EndOfInputException unused) {
      // expected
    }
  }
}
