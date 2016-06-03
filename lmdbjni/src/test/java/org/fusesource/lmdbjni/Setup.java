package org.fusesource.lmdbjni;

import java.io.File;

public class Setup {
  private static String ARCH = System.getProperty("os.arch").toLowerCase();
  private static String OS = System.getProperty("os.name").toLowerCase();

  public static boolean isWindows() {
    return (OS.indexOf("win") >= 0);
  }

  public static boolean isMac() {
    return (OS.indexOf("mac") >= 0);
  }

  public static boolean is32Bit() {
    return (ARCH.indexOf("i386") >= 0);
  }

  public static void setLmdbLibraryPath() {
    File path = new File(".");
    if (isWindows()) {
      path = new File(path, "../lmdbjni-win64/target/native-build/target/x64-release/lib");
    } else if (isMac()) {
      path = new File(path, "../lmdbjni-osx64/target/native-build/target/lib");
    } else if (is32Bit()){
      path = new File(path, "../lmdbjni-linux32/target/native-build/target/lib");
    } else {
      path = new File(path, "../lmdbjni-linux64/target/native-build/target/lib");
    }
    if (!path.exists()) {
      throw new IllegalStateException("Please build lmdbjni first " +
        "with a platform specific profile");
    }
    System.setProperty("library.lmdbjni.path", path.getAbsolutePath());
  }
}
