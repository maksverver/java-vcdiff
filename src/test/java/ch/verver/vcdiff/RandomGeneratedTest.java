package ch.verver.vcdiff;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class RandomGeneratedTest {

  @Test
  public void smallRandomData() throws Exception {
    runRandomTest(50 << 10, 50 << 10, 10);
  }

  @Test
  public void largeRandomData() throws Exception {
    runRandomTest(10 << 20, 10 << 20, 100);
  }

  private void runRandomTest(int minDictSize, int minTargetSize, int minNumWindows) throws CodecException {
    int maxDictSize = minDictSize + minDictSize/2;
    int maxTargetSize = minTargetSize + minTargetSize/2;
    int maxNumWindows = minNumWindows + minNumWindows/2;
    TestDataGenerator generator = new TestDataGenerator(minDictSize, maxDictSize, minTargetSize, maxTargetSize, minNumWindows, maxNumWindows);
    generator.generate();

    byte[] decoded = InMemoryDecoder.decode(generator.getDictionary(), generator.getDelta(),18 << 20);

    // Use plain junit.Assert because Truth can't cope with large arrays.
    Assert.assertTrue(Arrays.equals(decoded, generator.getTarget()));
  }
}
