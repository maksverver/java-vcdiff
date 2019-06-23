package ch.verver.vcdiff;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import org.junit.Assert;
import org.junit.Test;

public class ByteWriterTest {

  private static final byte B1 = 101;
  private static final byte B2 = 102;
  private static final byte B3 = 103;

  @Test
  public void emptyData() {
    ByteWriter w = new ByteWriter(new byte[0]);
    assertThat(w.position()).isEqualTo(0);
    assertThat(w.size()).isEqualTo(0);
    assertThat(w.atEnd()).isEqualTo(true);
  }

  @Test
  public void constructor() {
    byte[] data = new byte[3];
    assertThat(new ByteWriter(data, 0, 3).size()).isEqualTo(3);
    assertThat(new ByteWriter(data, 3, 0).size()).isEqualTo(0);
    assertThat(new ByteWriter(data, 1, 2).size()).isEqualTo(2);
    assertThat(new ByteWriter(data, 1, 2).position()).isEqualTo(0);
    assertConstructorThrowsIndexOutOfBoundsException(data, 0, 4);
    assertConstructorThrowsIndexOutOfBoundsException(data, 2, 2);
  }

  @Test
  public void asByteView() throws Exception {
    byte[] data = new byte[8];
    ByteWriter w = new ByteWriter(data, 2, 5);

    w.writeRun(B1, 1);
    assertThat(toArray(w.asByteView())).isEqualTo(new byte[]{B1});

    w.writeRun(B2, 2);
    assertThat(toArray(w.asByteView())).isEqualTo(new byte[]{B1, B2, B2});
  }

  @Test
  public void newSubWriter() throws Exception {
    byte[] data = new byte[8];
    ByteWriter w = new ByteWriter(data, 1, 6);
    ByteWriter sw1 = w.newSubWriter(2);
    ByteWriter sw2 = w.newSubWriter(2);
    assertThat(w.position()).isEqualTo(4);
    assertThat(sw1.position()).isEqualTo(0);
    assertThat(sw2.position()).isEqualTo(0);

    w.writeRun(B3, 1);
    sw1.writeRun(B1, 1);
    sw2.writeRun(B2, 1);
    assertThat(w.position()).isEqualTo(5);
    assertThat(sw1.position()).isEqualTo(1);
    assertThat(sw2.position()).isEqualTo(1);
    assertThat(sw1.atEnd()).isFalse();
    assertThat(sw2.atEnd()).isFalse();
    assertThat(data).isEqualTo(new byte[]{0, B1, 0, B2, 0, B3, 0, 0});

    sw1.writeRun(B1, 1);
    sw2.writeRun(B2, 1);
    assertThat(sw1.position()).isEqualTo(2);
    assertThat(sw2.position()).isEqualTo(2);
    assertThat(sw1.atEnd()).isTrue();
    assertThat(sw2.atEnd()).isTrue();
    assertWriteRunThrowsEndOfOutputException(sw1, B1, 1);
    assertWriteRunThrowsEndOfOutputException(sw2, B2, 1);

    w.writeRun(B3, 1);
    assertThat(toArray(w.asByteView())).isEqualTo(new byte[]{B1, B1, B2, B2, B3, B3});
  }

  @Test
  public void writeRun() throws Exception {
    byte[] data = new byte[8];
    ByteWriter w = new ByteWriter(data, 2, 5);
    w.writeRun(B1, 2);
    w.writeRun(B2, 3);
    assertThat(w.atEnd()).isTrue();
    w.writeRun(B3, 0);
    assertThat(data).isEqualTo(new byte[]{0, 0, B1, B1, B2, B2, B2, 0});
    assertThat(w.atEnd()).isTrue();
  }

  @Test
  public void writeRun_failBeforeEnd() throws Exception {
    ByteWriter w = new ByteWriter(new byte[4]);
    w.writeRun((byte) B1, 2);
    assertWriteRunThrowsEndOfOutputException(w, B2, 3);
  }

  @Test
  public void copyFromByteViewReader_basic() throws Exception {
    byte[] output = new byte[6];
    ByteViewReader r1 = new ByteViewReader(new ByteView(new byte[]{1, 2, 3}));
    ByteViewReader r2 = new ByteViewReader(new ByteView(new byte[]{4, 5, 6}));
    ByteWriter w = new ByteWriter(output);
    w.copyFrom(r1, 1);
    w.copyFrom(r2, 2);
    w.copyFrom(r1, 2);
    w.copyFrom(r2, 1);
    assertThat(r1.position()).isEqualTo(3);
    assertThat(r2.position()).isEqualTo(3);
    assertThat(w.position()).isEqualTo(6);
    assertThat(w.atEnd()).isTrue();
    assertThat(output).isEqualTo(new byte[]{1, 4, 5, 2, 3, 6});
  }

  @Test
  public void copyFromByteViewReader_withOffsets() throws Exception {
    byte[] input = new byte[]{10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
    byte[] output = new byte[10];
    ByteWriter w = new ByteWriter(output, 2, 8);
    ByteViewReader r = new ByteViewReader(new ByteView(input, 5, 6));
    w.copyFrom(r, 2);
    r.readByte();
    w.copyFrom(r, 3);
    assertThat(output).isEqualTo(new byte[]{0, 0, 15, 16, 18, 19, 20, 0, 0, 0});
  }

  @Test
  public void copyFromByteViewReader_endOfInput() throws Exception {
    ByteWriter w = new ByteWriter(new byte[3]);
    ByteViewReader r = new ByteViewReader(new ByteView(new byte[3]));
    w.writeRun((byte) 0, 1);
    r.readByte();
    r.readByte();
    // Now, writer has 2 bytes left, and reader has 1 byte left.
    try {
      w.copyFrom(r, 2);
      Assert.fail("Expected EndOfInputException to be thrown");
    } catch (ByteViewReader.EndOfInputException unused) {
      // expected
    }
  }

  @Test
  public void copyFromByteViewReader_endOfOutput() throws Exception {
    ByteWriter w = new ByteWriter(new byte[3]);
    ByteViewReader r = new ByteViewReader(new ByteView(new byte[3]));
    w.writeRun((byte) 0, 2);
    r.readByte();
    // Now, writer has 1 byte left, and reader has 2 bytes left.
    try {
      w.copyFrom(r, 2);
      Assert.fail("Expected EndOfOutputException to be thrown");
    } catch (ByteWriter.EndOfOutputException unused) {
      // expected
    }
  }

  @Test
  public void copyFromByteView() throws Exception {
    ByteView v = new ByteView(new byte[]{10, 11, 12, 13, 14, 15, 16});
    byte[] output = new byte[12];
    ByteWriter w = new ByteWriter(output, 2, 8);
    w.copyFrom(v, 4, 3);
    w.copyFrom(v, 0, 2);
    w.copyFrom(v, 3, 1);
    assertThat(w.position()).isEqualTo(6);
    assertThat(output).isEqualTo(new byte[]{0, 0, 14, 15, 16, 10, 11, 13, 0, 0, 0, 0});
    try {
      w.copyFrom(v, 0, 5);
      Assert.fail("Expected EndOfOutputException to be thrown");
    } catch (ByteWriter.EndOfOutputException unused) {
      // expected
    }
  }

  @Test
  public void copyFromThis() throws Exception {
    byte[] output = new byte[12];
    ByteWriter w = new ByteWriter(output, 1, 10);
    assertCopyFromThisThrowsIndexOutOfBoundsException(w, 0, 0);

    w.copyFrom(new ByteView(new byte[]{10, 11, 12, 13}), 0, 4);
    assertThat(w.position()).isEqualTo(4);
    assertCopyFromThisThrowsIndexOutOfBoundsException(w, 4, 1);

    w.copyFromThis(2, 2);
    assertThat(w.position()).isEqualTo(6);

    w.copyFromThis(0, 2);
    assertThat(w.position()).isEqualTo(8);

    w.copyFromThis(1, 2);
    assertThat(w.position()).isEqualTo(10);

    assertThat(output).isEqualTo(new byte[] {0, 10, 11, 12, 13, 12, 13, 10, 11, 11, 12, 0});
    try {
      w.copyFromThis(0, 2);
      Assert.fail("Expected EndOfOutputException to be thrown");
    } catch (ByteWriter.EndOfOutputException unused) {
      // expected
    }
  }

  @Test
  public void copyFromThis_duplicate() throws Exception {
    byte[] output = new byte[10];
    ByteWriter w = new ByteWriter(output, 2, 6);
    w.copyFrom(new ByteView(new byte[]{10, 11, 12}), 0, 3);
    w.copyFromThis(0, 3);
    assertThat(w.atEnd()).isTrue();
    assertThat(output).isEqualTo(new byte[]{0, 0, 10, 11, 12, 10, 11, 12, 0, 0});
  }

  @Test
  public void copyFromThis_replicate1() throws Exception {
    byte[] output = new byte[10];
    ByteWriter w = new ByteWriter(output, 2, 6);
    w.copyFrom(new ByteView(new byte[]{10, 11, 12}), 0, 3);
    w.copyFromThis(2, 3);
    assertThat(w.atEnd()).isTrue();
    assertThat(output).isEqualTo(new byte[]{0, 0, 10, 11, 12, 12, 12, 12, 0, 0});
  }

  @Test
  public void copyFromThis_replicate2() throws Exception {
    byte[] output = new byte[10];
    ByteWriter w = new ByteWriter(output, 1, 8);
    w.copyFrom(new ByteView(new byte[]{10, 11, 12}), 0, 3);
    w.copyFromThis(1, 5);
    assertThat(w.atEnd()).isTrue();
    assertThat(output).isEqualTo(new byte[]{0, 10, 11, 12, 11, 12, 11, 12, 11, 0});
  }

  @Test
  public void copyFromThis_replicate3() throws Exception {
    ByteWriter w = new ByteWriter(new byte[107], 7, 100);
    w.copyFrom(new ByteView(new byte[]{10, 11, 12}), 0, 3);
    w.copyFromThis(0, 30);
    assertThat(w.position()).isEqualTo(33);

    byte[] writtenOutput = toArray(w.asByteView());
    assertThat(writtenOutput.length).isEqualTo(33);
    for (int i = 0; i < writtenOutput.length; ++i) {
      assertWithMessage("writtenOutput[%s]", 1).that(writtenOutput[i]).isEqualTo(10 + i%3);
    }
  }

  private static void assertConstructorThrowsIndexOutOfBoundsException(byte[] data, int offset, int length) {
    try {
      new ByteWriter(data, offset, length);
      Assert.fail("Expected IndexOutOfBoundsException to be thrown");
    } catch (IndexOutOfBoundsException unused) {
      // expected
    }
  }


  private static void assertWriteRunThrowsEndOfOutputException(ByteWriter w, byte b, int len) {
    try {
      w.writeRun(b, len);
      Assert.fail("Expected EndOfOutputException to be thrown");
    } catch (ByteWriter.EndOfOutputException unused) {
      // expected
    }
  }

  private static void assertCopyFromThisThrowsIndexOutOfBoundsException(ByteWriter w, int pos, int len)
      throws ByteWriter.EndOfOutputException {
    try {
      w.copyFromThis(pos, len);
      Assert.fail("Expected IndexOutOfBoundsException to be thrown");
    } catch (IndexOutOfBoundsException unused) {
      // expected
    }
  }

  private static byte[] toArray(ByteView byteView) {
    int len = byteView.size();
    byte[] result = new byte[len];
    byteView.copyTo(0, result, 0, len);
    return result;
  }
}
