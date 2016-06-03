/**
 * Copyright (C) 2013, RedHat, Inc.
 *
 *    http://www.redhat.com/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fusesource.lmdbjni;

import java.nio.charset.Charset;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static org.fusesource.lmdbjni.JNI.mdb_strerror;
import static org.fusesource.lmdbjni.JNI.strlen;

/**
 * Some miscellaneous utility functions.
 *
 * @author <a href="http://hiramchirino.com">Hiram Chirino</a>
 */
class Util {
  public static final boolean isAndroid = isAndroid();

  public static String string(long ptr) {
    if (ptr == 0)
      return null;
    return new String(NativeBuffer.create(ptr, strlen(ptr)).toByteArray(),
      Charset.defaultCharset());
  }

  public static void checkErrorCode(int rc) {
    if (rc != 0) {
      String msg = string(mdb_strerror(rc));
      throw new LMDBException(msg, rc);
    }
  }

  public static void checkArgNotNull(Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException("The " + name + " argument cannot be null");
    }
  }

  static boolean isAndroid() {
    try {
      Class.forName("android.os.Process");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  static boolean is32Bit() {
    return Unsafe.ADDRESS_SIZE == 4;
  }
  
  /**
   * Fetches a C-style null terminated char[] from the specified buffer, returning
   * a {@link ByteString} representation.
   *
   * @param buffer to fetch the string from
   * @param offset index within the buffer to commence the null character search
   * @return the located string
   */
  public static ByteString getString(final DirectBuffer buffer,
                                     final int offset) {
    byte terminator = 1;
    int index = offset;
    while (terminator != (byte) 0) {
      terminator = buffer.getByte(index++);
    }
    byte[] bytes = new byte[index - offset - 1];
    buffer.getBytes(offset, bytes);
    return new ByteString(bytes);
  }

  /**
   * Stores a C-style null terminated char[] in the specified buffer.
   *
   * @param buffer to store the string in
   * @param offset index within the buffer to commence writing the value
   * @param value to store (should not contain any null character)
   */
  public static void putString(final MutableDirectBuffer buffer,
                               final int offset, final ByteString value) {
    final byte[] bytes = value.getBytes();
    buffer.putBytes(offset, bytes);
    buffer.putByte(offset + bytes.length, (byte) 0);
  }
}
