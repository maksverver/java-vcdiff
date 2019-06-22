package ch.verver.vcdiff;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.Test;

public class ByteViewTest {
  @Test
  public void nullData() {
    assertThrows(NullPointerException.class, () -> new ByteView(null));
  }

  @Test
  public void emptyData() {
    ByteView v = new ByteView(new byte[0]);
    assertThat(v.size()).isEqualTo(0);
    assertThat(v.subView(0, 0)).isNotNull();
    assertThrows(IndexOutOfBoundsException.class, () -> v.get(0));
  }

  @Test
  public void wholeArray() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12 });
    assertThat(v.size()).isEqualTo(3);
    assertThat(v.get(0)).isEqualTo(10);
    assertThat(v.get(1)).isEqualTo(11);
    assertThat(v.get(2)).isEqualTo(12);
    assertThrows(IndexOutOfBoundsException.class, () -> v.get(3));
    assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
  }

  @Test
  public void subArray() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12, 13, 14, 15 }, 2, 3);
    assertThat(v.size()).isEqualTo(3);
    assertThat(v.get(0)).isEqualTo(12);
    assertThat(v.get(1)).isEqualTo(13);
    assertThat(v.get(2)).isEqualTo(14);
    assertThrows(IndexOutOfBoundsException.class, () -> v.get(3));
    assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
  }

  @Test
  public void subView() {
    ByteView v = new ByteView(new byte[] { 10, 11, 12, 13, 14, 15 });
    assertThrows(IndexOutOfBoundsException.class, () -> v.subView(0, 7));
    assertThrows(IndexOutOfBoundsException.class, () -> v.subView(1, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> v.subView(7, 0));
    assertThrows(IndexOutOfBoundsException.class, () -> v.subView(2, -1));
    assertThrows(IndexOutOfBoundsException.class, () -> v.subView(-1, 0));
    assertThat(v.subView(0, 0)).isNotNull();
    assertThat(v.subView(0, 6)).isNotNull();
    assertThat(v.subView(6, 0)).isNotNull();
    assertThat(v.subView(1, 5)).isNotNull();

    ByteView w = v.subView(2, 3);
    assertThat(w.size()).isEqualTo(3);
    assertThat(w.get(0)).isEqualTo(12);
    assertThat(w.get(1)).isEqualTo(13);
    assertThat(w.get(2)).isEqualTo(14);
    assertThrows(IndexOutOfBoundsException.class, () -> w.get(3));
    assertThrows(IndexOutOfBoundsException.class, () -> w.get(-1));
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
}
