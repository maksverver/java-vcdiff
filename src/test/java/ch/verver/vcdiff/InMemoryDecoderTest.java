package ch.verver.vcdiff;

import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class InMemoryDecoderTest {
  @Test
  public void resourceTest1() throws Exception {
    runTestCaseFromResource("/test-1");
  }

  @Test
  public void resourceTest2() throws Exception {
    runTestCaseFromResource("/test-2");
  }

  private void runTestCaseFromResource(String pathPrefix) throws Exception {
    byte[] dict = loadResource(pathPrefix + "-dictionary");
    byte[] delta = loadResource(pathPrefix + "-delta");
    byte[] target = loadResource(pathPrefix + "-target");
    byte[] decoded = InMemoryDecoder.decode(dict, delta, Integer.MAX_VALUE);
    assertTrue(Arrays.equals(decoded, target));
  }

  private static byte[] loadResource(String path) throws IOException {
    InputStream is = InMemoryDecoderTest.class.getResourceAsStream(path);
    if (is == null) {
      throw new AssertionError(String.format(Locale.US, "Resource [%s] does not exist!", path));
    }
    return IOUtils.toByteArray(is);
  }
}
