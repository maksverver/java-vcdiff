package vcdiff;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.nio.file.Files;

public class Main {
  private static final String PRINT_CODE_TABLE_COMMAND = "printcodetable";
  private static final String DECODE_COMMAND = "decode";

  public static void main(String... args) throws IOException, CodecException {
    if (args.length == 1 && PRINT_CODE_TABLE_COMMAND.equals(args[0])) {
      printDefaultCodeTable();
      return;
    }

    if (args.length == 4 && DECODE_COMMAND.equals(args[0])) {
      byte[] dictionaryBytes = Files.readAllBytes(new File(args[1]).toPath());
      byte[] deltaBytes = Files.readAllBytes(new File(args[2]).toPath());
      byte[] targetBytes = InMemoryDecoder.decode(dictionaryBytes, deltaBytes, Integer.MAX_VALUE);
      Files.write(new File(args[3]).toPath(), targetBytes);
      return;
    }

    System.out.println("Usage:\n\n\tjava vcdiff.Main <command> [<arguments>...]\n");
    System.out.println("Possible commands:\n");
    System.out.println("\t" + PRINT_CODE_TABLE_COMMAND);
    System.out.println("\t" + DECODE_COMMAND + " <dictionary> <delta> <target>");
  }

  private static void printDefaultCodeTable() {
    System.out.println("IDX  TYPE SIZE MODE  TYPE SIZE MODE");
    System.out.println("-----------------------------------");
    for (int i = 0; i < 256; ++i) {
      int instr = CodeTable.getInstructions(i);
      int type1 = CodeTable.instructionType(instr);
      int size1 = CodeTable.instructionSize(instr);
      int mode1 = CodeTable.instructionMode(instr);
      int instr2 = CodeTable.nextInstruction(instr);
      int type2 = CodeTable.instructionType(instr2);
      int size2 = CodeTable.instructionSize(instr2);
      int mode2 = CodeTable.instructionMode(instr2);
      if (CodeTable.nextInstruction(instr2) != 0) {
        throw new AssertionError("More than 2 combined instructions in code table.");
      }
      String mnem1 = instructionTypeToString(type1);
      String mnem2 = instructionTypeToString(type2);
      System.out.printf(Locale.US, "%3d  %-4s %4d %4d  %-4s %4d %4d\n",
          i, mnem1, size1, mode1, mnem2, size2, mode2);
    }
    System.out.println("-----------------------------------");
  }

  private static String instructionTypeToString(int i) {
    switch (i) {
      case 0:
        return "NOOP";
      case 1:
        return "ADD";
      case 2:
        return "RUN";
      case 3:
        return "COPY";
    }
    return "???";
  }
}
