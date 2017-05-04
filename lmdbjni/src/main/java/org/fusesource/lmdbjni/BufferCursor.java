package org.fusesource.lmdbjni;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.fusesource.lmdbjni.JNI.mdb_strerror;

/**
 * Cursor mode that allow for zero-copy lookup, navigation and modification
 * by using addresses provided by LMDB instead of copying data for each
 * operation.
 * <p/>
 * <p>
 * This is an advanced mode. Users should avoid interacting directly
 * with DirectBuffer and use the BufferCursor API instead. Otherwise
 * take extra care of buffer memory address+size and byte ordering.
 * Mistakes may lead to SIGSEGV or unpredictable key ordering etc.
 * </p>
 * <p/>
 * <p>
 * Key and value buffers are updated as the cursor moves. After a
 * cursor have been moved, the buffers will point to a memory address
 * owned by the database. The caller need not dispose of the memory,
 * and may not modify it in any way. Modification to buffers in this
 * state will cause a SIGSEGV.
 * </p>
 * <p>
 * Cursors start in an unpositioned state. If {@link BufferCursor#next()} or
 * {@link BufferCursor#prev()} ()} are used in this state, iteration proceeds
 * from the start or end respectively.
 * Cursors return to an unpositioned state once any scanning or seeking method
 * returns <code>false</code>. This is primarily to ensure safe, consistent
 * semantics in the face of any error condition.
 * When the Cursor returns to an unpositioned state, its {@link BufferCursor#keyLength()}}
 * and {@link BufferCursor#valLength()} return <code>0</code> to indicate there is no
 * active position, although internally the LMDB cursor may still have a valid position.
 * Similarly methods like {@link BufferCursor#keyByte(int)} or {@link BufferCursor#valByte(int)}
 * will throw an {@link IndexOutOfBoundsException}.
 * </p>
 * <p>
 * Any modification will be written into cached byte buffers which
 * is not written into database until {@link BufferCursor#put()} or
 * {@link BufferCursor#overwrite()} is called and no updates are visible
 * outside the transaction until the transaction is committed. The
 * value byte buffer expand as data is written into it. The key buffer
 * capacity is 511 which is the max key size in LMDB (unless recompiled
 * from source with other capacity).
 * </p>
 * <p>
 * As soon as a key or value is written, the cursor still maintain its
 * position but the initial view into the database is lost and will
 * need to be re-established.
 * </p>
 * <p>
 * The BufferCursor writes data in big endian (Java default) whereas direct
 * buffers write in native byte order (usually little endian) which may not
 * be desired for certain key ordering schemes.
 * </p>
 * <p/>
 * BufferCopy is not thread safe and should be closed by the same thread
 * that opened it.
 * <p>
 * Do not use on Android.
 * </p>
 * <p/>
 * <pre>
 * {@code
 *
 * // read only
 * try (Transaction tx = env.createReadTransaction();
 *      BufferCursor cursor = db.bufferCursor(tx)) {
 *   // iterate from the first entry to the last
 *   if (cursor.first()) {
 *     do {
 *       cursor.keyByte(0);
 *       cursor.valByte(0);
 *     }  while(cursor.next());
 *   }
 *
 *   // iterate from the last entry to the first
 *   if (cursor.last()) {
 *     do {
 *       cursor.keyByte(0);
 *       cursor.valByte(0);
 *     } while(cursor.prev());
 *   }
 *
 *   // find entry matching exactly the provided key
 *   cursor.keyWriteBytes(bytes("Paris"));
 *   if (cursor.seekKey()) {
 *     cursor.keyByte(0);
 *     cursor.valByte(0);
 *   }
 *
 *   // find first entry which key greater than or equal to the provided key
 *   cursor.keyWriteBytes(bytes("London"));
 *   if (cursor.seekRange()) {
 *     cursor.keyByte(0);
 *     cursor.valByte(0);
 *   }
 * }
 *
 * // open for write
 * try (Transaction tx = env.createWriteTransaction();
 *      BufferCursor cursor = db.bufferCursor(tx)) {
 *   cursor.first();
 *   cursor.keyWriteUtf8("England");
 *   cursor.valWriteUtf8("London");
 *   cursor.overwrite();
 *   cursor.first();
 *   cursor.delete();
 * }
 * }
 * </pre>
 *
 * @author Kristoffer Sjögren
 */
public class BufferCursor implements AutoCloseable {
  private final Cursor cursor;
  private final ByteBuffer keyByteBuffer;
  private ByteBuffer valueByteBuffer;
  private final boolean isReadOnly;
  private DirectBuffer key;
  private DirectBuffer value;
  private boolean keyDatbaseMemoryLocation = false;
  private boolean valDatbaseMemoryLocation = false;
  private int keyWriteIndex = 0;
  private int valWriteIndex = 0;
  private boolean validPosition = false;

  BufferCursor(Cursor cursor, DirectBuffer key, DirectBuffer value) {
    this.cursor = cursor;
    this.isReadOnly = cursor.isReadOnly();
    if (key.byteBuffer() == null) {
      throw new IllegalArgumentException("No ByteBuffer available for key.");
    }
    if (!key.byteBuffer().isDirect()) {
      throw new IllegalArgumentException("ByteBuffer for key must be direct.");
    }
    this.keyByteBuffer = key.byteBuffer();
    this.key = key;
    if (value.byteBuffer() == null) {
      throw new IllegalArgumentException("No ByteBuffer available for value.");
    }
    if (!value.byteBuffer().isDirect()) {
      throw new IllegalArgumentException("ByteBuffer for value must be direct.");
    }
    this.value = value;
    this.valueByteBuffer = value.byteBuffer();
  }

  BufferCursor(Cursor cursor, int maxValueSize) {
    this(cursor, new DirectBuffer(), new DirectBuffer(ByteBuffer.allocateDirect(maxValueSize)));
  }

  /**
   * @return the write position of the key
   */
  public int keyWriteIndex() {
	  return keyWriteIndex;
  }

  /**
   * @return the write position of the value
   */
  public int valWriteIndex() {
	  return valWriteIndex;
  }

  /**
   * Position at the exact provided key.
   *
   * @return true if a key was found.
   */
  public boolean seekKey() {
    if (keyWriteIndex != 0) {
      this.key.wrap(this.key.addressOffset(), keyWriteIndex);
    }
    int rc = cursor.seekPosition(this.key, value, SeekOp.KEY);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at first key greater than or equal to provided key.
   *
   * @return true if a key was found.
   */
  public boolean seekRange() {
    if (keyWriteIndex != 0) {
      this.key.wrap(this.key.addressOffset(), keyWriteIndex);
    }
    int rc = cursor.seekPosition(this.key, value, SeekOp.RANGE);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at first key greater than or equal to specified key.
   *
   * @param key key to seek for.
   * @return true if a key was found.
   */
  public boolean seekRange(byte[] key) {
    keyWriteBytes(key);
    return seekRange();
  }

  /**
   * Position at first key greater than or equal to specified key.
   *
   * @param key key to seek for.
   * @return true if a key was found.
   * @deprecated use {@link BufferCursor#seekRange(byte[])} }
   */
  @Deprecated
  public boolean seek(byte[] key) {
    return seekRange(key);
  }

  /**
   * Position at first key/data item.
   *
   * @return true if found
   */
  public boolean first() {
    int rc = cursor.position(key, value, GetOp.FIRST);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at first data item of current key. Only for
   * {@link org.fusesource.lmdbjni.Constants#DUPSORT}.
   *
   * @return true if found
   */
  public boolean firstDup() {
    int rc = cursor.position(key, value, GetOp.FIRST_DUP);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at last key/data item.
   *
   * @return true if found
   */
  public boolean last() {
    int rc = cursor.position(key, value, GetOp.LAST);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at first data item of current key. Only for
   * {@link org.fusesource.lmdbjni.Constants#DUPSORT}.
   *
   * @return true if found
   */
  public boolean lastDup() {
    int rc = cursor.position(key, value, GetOp.LAST_DUP);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at next data item.
   *
   * @return true if found
   */
  public boolean next() {
    int rc = cursor.position(key, value, GetOp.NEXT);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at next data item of current key. Only for
   * {@link org.fusesource.lmdbjni.Constants#DUPSORT}.
   *
   * @return true if found
   */
  public boolean nextDup() {
    int rc = cursor.position(key, value, GetOp.NEXT_DUP);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }


  /**
   * Position at previous data item.
   *
   * @return true if found
   */
  public boolean prev() {
    int rc = cursor.position(key, value, GetOp.PREV);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * Position at previous data item of current key. Only for
   * {@link org.fusesource.lmdbjni.Constants#DUPSORT}.
   *
   * @return true if found
   */
  public boolean prevDup() {
    int rc = cursor.position(key, value, GetOp.PREV_DUP);
    setDatabaseMemoryLocation(rc);
    return rc == 0;
  }

  /**
   * <p>
   * Delete key/data pair at current cursor position.
   * </p>
   */
  public void delete() {
    cursor.delete();
  }


  /**
   * <p>
   * Delete key/data. Only for
   * {@link org.fusesource.lmdbjni.Constants#DUPSORT}.
   * </p>
   */
  public void deleteDup() {
    cursor.deleteIncludingDups();
  }

  /**
   * Close the cursor and the transaction.
   */
  @Override
  public void close() {
    cursor.close();
  }

  /**
   * Stores key/data pairs in the database replacing any
   * previously existing key.
   */
  public boolean put() {
    DirectBuffer k = (keyWriteIndex != 0) ?
      new DirectBuffer(key.addressOffset(), keyWriteIndex) : key;
    DirectBuffer v = (valWriteIndex != 0) ?
      new DirectBuffer(value.addressOffset(), valWriteIndex) : value;
    keyWriteIndex = 0;
    valWriteIndex = 0;
    int rc = cursor.put(k, v, Constants.NOOVERWRITE);
    if (rc == 0) {
      return true;
    } else if (rc == LMDBException.KEYEXIST) {
      return false;
    } else {
      String msg = Util.string(mdb_strerror(rc));
      throw new LMDBException(msg, rc);
    }
  }


  /**
   * general purpose put method, so client can have more control
   * of the flag they need.
   */
  public boolean put(int flag) {
    DirectBuffer k = (keyWriteIndex != 0) ?
            new DirectBuffer(key.addressOffset(), keyWriteIndex) : key;
    DirectBuffer v = (valWriteIndex != 0) ?
            new DirectBuffer(value.addressOffset(), valWriteIndex) : value;
    keyWriteIndex = 0;
    valWriteIndex = 0;
    int rc = cursor.put(k, v, flag);
    if (rc == 0) {
      return true;
    } else {
      String msg = Util.string(mdb_strerror(rc));
      throw new LMDBException(msg, rc);
    }
  }



  /**
   * Stores key/data pairs in the database replacing any
   * previously existing key. Also used for adding duplicates.
   */
  public boolean overwrite() {
    DirectBuffer k = (keyWriteIndex != 0) ?
      new DirectBuffer(key.addressOffset(), keyWriteIndex) : key;
    DirectBuffer v = (valWriteIndex != 0) ?
      new DirectBuffer(value.addressOffset(), valWriteIndex) : value;
    keyWriteIndex = 0;
    valWriteIndex = 0;
    int rc = cursor.put(k, v, 0);
    if (rc == 0) {
      return true;
    } else if (rc == LMDBException.KEYEXIST) {
      return false;
    } else {
      String msg = Util.string(mdb_strerror(rc));
      throw new LMDBException(msg, rc);
    }
  }

  /**
   * Append the given key/data pair to the end of the database.
   * No key comparisons are performed. This option allows
   * fast bulk loading when keys are already known to be in the
   * correct order. Loading unsorted keys with this flag will cause
   * data corruption.
   */
  public void append() {
    DirectBuffer k = (keyWriteIndex != 0) ?
      new DirectBuffer(key.addressOffset(), keyWriteIndex) : key;
    DirectBuffer v = (valWriteIndex != 0) ?
      new DirectBuffer(value.addressOffset(), valWriteIndex) : value;
    keyWriteIndex = 0;
    valWriteIndex = 0;
    int rc = cursor.put(k, v, Constants.APPEND);
    if (rc != 0) {
      String msg = Util.string(mdb_strerror(rc));
      throw new LMDBException(msg, rc);
    }
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data boolean
   * @return this
   */
  public BufferCursor keyWriteBoolean(boolean data) {
    setSafeKeyMemoryLocation();
    this.key.putByte(keyWriteIndex, data ? (byte)1 : (byte)0);
    keyWriteIndex += 1;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data byte
   * @return this
   */
  public BufferCursor keyWriteByte(int data) {
    setSafeKeyMemoryLocation();
    this.key.putByte(keyWriteIndex, (byte) data);
    keyWriteIndex += 1;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data short
   * @return this
   */
  public BufferCursor keyWriteShort(short data) {
    setSafeKeyMemoryLocation();
    this.key.putShort(keyWriteIndex, data, ByteOrder.BIG_ENDIAN);
    keyWriteIndex += 2;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data int
   * @return this
   */
  public BufferCursor keyWriteInt(int data) {
    setSafeKeyMemoryLocation();
    this.key.putInt(keyWriteIndex, data, ByteOrder.BIG_ENDIAN);
    keyWriteIndex += 4;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data long
   * @return this
   */
  public BufferCursor keyWriteLong(long data) {
    setSafeKeyMemoryLocation();
    this.key.putLong(keyWriteIndex, data, ByteOrder.BIG_ENDIAN);
    keyWriteIndex += 8;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data float
   * @return this
   */
  public BufferCursor keyWriteFloat(float data) {
    setSafeValMemoryLocation();
    this.key.putFloat(keyWriteIndex, data, ByteOrder.BIG_ENDIAN);
    keyWriteIndex += 4;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data double
   * @return this
   */
  public BufferCursor keyWriteDouble(double data) {
    setSafeValMemoryLocation();
    this.key.putDouble(keyWriteIndex, data, ByteOrder.BIG_ENDIAN);
    keyWriteIndex += 8;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data string
   * @return this
   */
  public BufferCursor keyWriteUtf8(ByteString data) {
    setSafeKeyMemoryLocation();
    this.key.putString(keyWriteIndex, data);
    keyWriteIndex += data.size() + 1;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data string
   * @return this
   */
  public BufferCursor keyWriteUtf8(String data) {
    setSafeKeyMemoryLocation();
    ByteString bytes = new ByteString(data);
    this.key.putString(keyWriteIndex, bytes);
    keyWriteIndex += bytes.size() + 1;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data byte array
   * @return this
   */
  public BufferCursor keyWriteBytes(byte[] data) {
    setSafeKeyMemoryLocation();
    this.key.putBytes(keyWriteIndex, data);
    keyWriteIndex += data.length;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param data byte array
   * @param offset the start offset in the data
   * @param length the number of bytes to write
   * @return this
   */
  public BufferCursor keyWriteBytes(byte[] data, int offset, int length) {
    setSafeKeyMemoryLocation();
    this.key.putBytes(keyWriteIndex, data, offset, length);
    keyWriteIndex += length;
    return this;
  }

  /**
   * Write data to key at current cursor position and
   * move write index forward.
   *
   * @param buffer   buffer
   * @param capacity capacity
   * @return this
   */
  public BufferCursor keyWrite(DirectBuffer buffer, int capacity) {
    setSafeKeyMemoryLocation();
    this.key.putBytes(keyWriteIndex, buffer, 0, capacity);
    keyWriteIndex += capacity;
    return this;
  }

  /**
   * @see org.fusesource.lmdbjni.BufferCursor#keyWrite(DirectBuffer, int)
   */
  public BufferCursor keyWrite(DirectBuffer buffer) {
    keyWrite(buffer, buffer.capacity());
    return this;
  }

  /**
   * Get key length at current cursor position.
   *
   * @return length of key or <code>0</code> if cursor is in an unpositioned state.
   */
  public int keyLength() {
    if (validPosition) {
      return this.key.capacity();
    } else {
      return 0;
    }
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return boolean
   */
  public boolean keyBoolean(int pos) {
    checkForValidPosition();
    return this.key.getByte(pos) == (byte)1;
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return byte
   */
  public byte keyByte(int pos) {
    checkForValidPosition();
    return this.key.getByte(pos);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return short
   */
  public short keyShort(int pos) {
    checkForValidPosition();
    return this.key.getShort(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return int
   */
  public int keyInt(int pos) {
    checkForValidPosition();
    return this.key.getInt(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return long
   */
  public long keyLong(int pos) {
    checkForValidPosition();
    return this.key.getLong(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return float
   */
  public float keyFloat(int pos) {
    checkForValidPosition();
    return this.key.getFloat(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return double
   */
  public double keyDouble(int pos) {
    checkForValidPosition();
    return this.key.getDouble(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get string data from key (ending with NULL byte)
   * at current cursor position.
   *
   * @param pos byte position
   * @return String
   */
  public ByteString keyUtf8(int pos) {
    checkForValidPosition();
    return this.key.getString(pos);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return byte array
   */
  public byte[] keyBytes(int pos, int length) {
    checkForValidPosition();
    byte[] k = new byte[length];
    key.getBytes(pos, k);
    return k;
  }

  /**
   * @return copy of key data
   */
  public byte[] keyBytes() {
    checkForValidPosition();
    byte[] k = new byte[key.capacity()];
    key.getBytes(0, k);
    return k;
  }

  /**
   * Here be dragons, use with caution!
   *
   * @return underlying buffer
   */
  public DirectBuffer keyBuffer() {
    return key;
  }

  /**
   * @return the key direct buffer at current position.
   */
  public DirectBuffer keyDirectBuffer() {
    checkForValidPosition();
    return key;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data boolean
   * @return this
   */
  public BufferCursor valWriteBoolean(boolean data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(1);
    this.value.putByte(valWriteIndex, data ? (byte)1 : (byte)0);
    valWriteIndex += 1;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data byte
   * @return this
   */
  public BufferCursor valWriteByte(int data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(1);
    this.value.putByte(valWriteIndex, (byte) data);
    valWriteIndex += 1;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data short
   * @return this
   */
  public BufferCursor valWriteShort(short data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(4);
    this.value.putShort(valWriteIndex, data, ByteOrder.BIG_ENDIAN);
    valWriteIndex += 2;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data int
   * @return this
   */
  public BufferCursor valWriteInt(int data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(4);
    this.value.putInt(valWriteIndex, data, ByteOrder.BIG_ENDIAN);
    valWriteIndex += 4;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data long
   * @return this
   */
  public BufferCursor valWriteLong(long data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(8);
    this.value.putLong(valWriteIndex, data, ByteOrder.BIG_ENDIAN);
    valWriteIndex += 8;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data float
   * @return this
   */
  public BufferCursor valWriteFloat(float data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(4);
    this.value.putFloat(valWriteIndex, data, ByteOrder.BIG_ENDIAN);
    valWriteIndex += 4;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data float
   * @return this
   */
  public BufferCursor valWriteDouble(double data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(8);
    this.value.putDouble(valWriteIndex, data, ByteOrder.BIG_ENDIAN);
    valWriteIndex += 8;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data string
   * @return this
   */
  public BufferCursor valWriteUtf8(String data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ByteString bytes = new ByteString(data);
    ensureValueWritableBytes(bytes.size() + 1);
    this.value.putString(valWriteIndex, bytes);
    valWriteIndex += bytes.size() + 1;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data string
   * @return this
   */
  public BufferCursor valWriteUtf8(ByteString data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(data.size() + 1);
    this.value.putString(valWriteIndex, data);
    valWriteIndex += data.size() + 1;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data byte array
   * @return this
   */
  public BufferCursor valWriteBytes(byte[] data) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(data.length);
    this.value.putBytes(valWriteIndex, data);
    valWriteIndex += data.length;
    return this;
  }

  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param data byte array
  * @param offset the start offset in the data
  * @param length the number of bytes to write
   * @return this
   */
  public BufferCursor valWriteBytes(byte[] data, int offset, int length) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(length);
    this.value.putBytes(valWriteIndex, data, offset, length);
    valWriteIndex += length;
    return this;
  }


  
  /**
   * Write data to value at current cursor position and
   * move write index forward.
   *
   * @param buffer to write from
   * @param srcIndex position to start in buffer
   * @param length how many bytes to write
   * @return this
   */
  public BufferCursor valWrite(DirectBuffer buffer, int srcIndex, int length) {
    if (isReadOnly) {
      throw new LMDBException("Read only transaction", LMDBException.EACCES);
    }
    setSafeValMemoryLocation();
    ensureValueWritableBytes(length);
    this.value.putBytes(valWriteIndex, buffer, srcIndex, length);
    valWriteIndex += length;
    return this;
  }

  /**
   * @see org.fusesource.lmdbjni.BufferCursor#valWrite(DirectBuffer, int, int)
   */
  public BufferCursor valWrite(DirectBuffer buffer, int length) {
    return valWrite(buffer, 0, length);
  }

  /**
   * @see org.fusesource.lmdbjni.BufferCursor#valWrite(DirectBuffer, int)
   */
  public BufferCursor valWrite(DirectBuffer buffer) {
    valWrite(buffer, 0, buffer.capacity());
    return this;
  }

  /**
   * Get value length at current cursor position.
   *
   * @return length of value or <code>0</code> if cursor is in an unpositioned state.
   */
  public int valLength() {
    if (validPosition) {
      return this.value.capacity();
    } else {
      return 0;
    }
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return boolean
   */
  public boolean valBoolean(int pos) {
    checkForValidPosition();
    return this.value.getByte(pos) == (byte)1;
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return byte
   */
  public byte valByte(int pos) {
    checkForValidPosition();
    return this.value.getByte(pos);
  }

  private void checkForValidPosition() {
    if (!validPosition) {
      throw new IndexOutOfBoundsException("Cursor is in an unpositioned state");
    }
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return short
   */
  public short valShort(int pos) {
    checkForValidPosition();
    return this.value.getShort(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return int
   */
  public int valInt(int pos) {
    checkForValidPosition();
    return this.value.getInt(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return long
   */
  public long valLong(int pos) {
    checkForValidPosition();
    return this.value.getLong(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from key at current cursor position.
   *
   * @param pos byte position
   * @return byte array
   */
  public byte[] valBytes(int pos, int length) {
    checkForValidPosition();
    byte[] v = new byte[length];
    value.getBytes(pos, v);
    return v;
  }


  /**
   * @return copy of value data
   */
  public byte[] valBytes() {
    checkForValidPosition();
    byte[] v = new byte[value.capacity()];
    value.getBytes(0, v);
    return v;
  }

  /**
   * Here be dragons, use with caution!
   *
   * @return underlying buffer
   */
  public DirectBuffer valBuffer() {
    return value;
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return float
   */
  public float valFloat(int pos) {
    checkForValidPosition();
    return this.value.getFloat(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get data from value at current cursor position.
   *
   * @param pos byte position
   * @return double
   */
  public double valDouble(int pos) {
    checkForValidPosition();
    return this.value.getDouble(pos, ByteOrder.BIG_ENDIAN);
  }

  /**
   * Get string data from key (ending with NULL byte)
   * at current cursor position.
   *
   * @param pos byte position
   * @return String
   */
  public ByteString valUtf8(int pos) {
    checkForValidPosition();
    return this.value.getString(pos);
  }

  /**
   * @return the direct buffer at the current position.
   */
  public DirectBuffer valDirectBuffer() {
    checkForValidPosition();
    return this.value;
  }

  /**
   * Prepare cursor for write.
   * <p/>
   * Only needed by users that manage DirectBuffer on their own.
   */
  public void setWriteMode() {
    setSafeKeyMemoryLocation();
    setSafeValMemoryLocation();
  }

  private void setSafeKeyMemoryLocation() {
    if (keyDatbaseMemoryLocation) {
      this.key.wrap(keyByteBuffer);
      keyDatbaseMemoryLocation = false;
    }
  }

  private void setSafeValMemoryLocation() {
    if (valDatbaseMemoryLocation) {
      this.value.wrap(valueByteBuffer);
      valDatbaseMemoryLocation = false;
    }
  }

  private void setDatabaseMemoryLocation(int rc) {
    validPosition = rc == 0;
    this.valDatbaseMemoryLocation = true;
    this.keyDatbaseMemoryLocation = true;
    keyWriteIndex = 0;
    valWriteIndex = 0;
  }

  private void ensureValueWritableBytes(int minWritableBytes) {
    if (minWritableBytes <= (valueByteBuffer.capacity() - valWriteIndex)) {
      return;
    }

    int newCapacity;
    if (valueByteBuffer.capacity() == 0) {
      newCapacity = 1;
    } else {
      newCapacity = valueByteBuffer.capacity();
    }
    int minNewCapacity = valWriteIndex + minWritableBytes;
    while (newCapacity < minNewCapacity) {
      newCapacity <<= 1;
      // exceeded maximum size of 2gb, then newCapacity == 0
      if (newCapacity == 0) {
        throw new IllegalStateException("Maximum size of 2gb exceeded");
      }
    }
    ByteBuffer newBuffer = ByteBuffer.allocateDirect(newCapacity).order(valueByteBuffer.order());
    valueByteBuffer.position(0);
    newBuffer.put(valueByteBuffer);
    valueByteBuffer = newBuffer;
    this.value.wrap(valueByteBuffer);
  }
}
