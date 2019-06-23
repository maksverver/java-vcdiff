package ch.verver.vcdiff;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Assert;
import org.junit.Test;

public class ByteViewTest {
  @Test
  public void nullData() {
    try {
      new ByteView(null);
      Assert.fail("Expected NullPointerException to be thrown");
    } catch (NullPointerException unused) {
      // expected
    }
  }

  @Test
  public void emptyData() {
    ByteView v = new ByteView(new byte[0]);
    assertThat(v.size()).isEqualTo(0);
    assertThat(v.subView(0, 0)).isNotNull();
    assertGetThrowsIndexOutOfBoundsException(v, 0);
  }

  @Test
  public void wholeArray() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12 });
    assertThat(v.size()).isEqualTo(3);
    assertThat(v.get(0)).isEqualTo(10);
    assertThat(v.get(1)).isEqualTo(11);
    assertThat(v.get(2)).isEqualTo(12);
    assertGetThrowsIndexOutOfBoundsException(v, 3);
    assertGetThrowsIndexOutOfBoundsException(v, -1);
  }

  @Test
  public void subArray() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12, 13, 14, 15 }, 2, 3);
    assertThat(v.size()).isEqualTo(3);
    assertThat(v.get(0)).isEqualTo(12);
    assertThat(v.get(1)).isEqualTo(13);
    assertThat(v.get(2)).isEqualTo(14);
    assertGetThrowsIndexOutOfBoundsException(v, 3);
    assertGetThrowsIndexOutOfBoundsException(v, -1);
  }

  @Test
  public void subView() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12, 13, 14, 15 });

    assertSubViewThrowsIndexOutOfBoundsExceptions(v, 0, 7);
    assertSubViewThrowsIndexOutOfBoundsExceptions(v, 1, 6);
    assertSubViewThrowsIndexOutOfBoundsExceptions(v, 7, 0);
    assertSubViewThrowsIndexOutOfBoundsExceptions(v, 2, -1);
    assertSubViewThrowsIndexOutOfBoundsExceptions(v, -1, 0);
    assertThat(v.subView(0, 0)).isNotNull();
    assertThat(v.subView(0, 6)).isNotNull();
    assertThat(v.subView(6, 0)).isNotNull();
    assertThat(v.subView(1, 5)).isNotNull();

    ByteView w = v.subView(2, 3);
    assertThat(w.size()).isEqualTo(3);
    assertThat(w.get(0)).isEqualTo(12);
    assertThat(w.get(1)).isEqualTo(13);
    assertThat(w.get(2)).isEqualTo(14);
    assertGetThrowsIndexOutOfBoundsException(w, 3);
    assertGetThrowsIndexOutOfBoundsException(w, -1);
  }

  @Test
  public void copyTo() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12, 13, 14, 15 }, 2, 3);
    byte[] bytes = new byte[8];
    v.copyTo(0, bytes, 0, 3);
    v.copyTo(0, bytes, 4, 2);
    v.copyTo(1, bytes, 6, 2);
    assertThat(bytes).isEqualTo(new byte[] { 12, 13, 14, 0, 12, 13, 13, 14 });
  }

  private static void assertGetThrowsIndexOutOfBoundsException(ByteView v, int index) {
    try {
      v.get(index);
      Assert.fail("Expected IndexOutOfBoundsException to be thrown");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
  }
  private static void assertSubViewThrowsIndexOutOfBoundsExceptions(ByteView v, int pos, int len) {
    try {
      v.subView(pos, len);
      Assert.fail("Expected IndexOutOfBoundsException to be thrown");
    } catch (IndexOutOfBoundsException e) {
      // expected
    }
  }

}
