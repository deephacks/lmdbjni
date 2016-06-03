package org.fusesource.lmdbjni;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.Comparator;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

public class ComparatorTest {
  static {
    Setup.setLmdbLibraryPath();
  }

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  Env env;
  Database db;
  byte[] data = new byte[] {1,2,3};

  @Before
  public void before() throws IOException {
    String path = tmp.newFolder().getCanonicalPath();
    env = new Env();
    env.setMapSize(16 * 4096);
    env.open(path);
    db = env.openDatabase();
  }

  @After
  public void after() {
    db.close();
    env.close();
  }

  @Test
  public void testSetComparatorAsc() {
    if (Util.isAndroid() || Util.is32Bit()) {
      return;
    }

    Transaction writeTransaction = env.createWriteTransaction();
    db.setComparator(writeTransaction, new Comparator<byte[]>() {
      @Override
      public int compare(byte[] key1, byte[] key2) {
        return (int) (Bytes.getLong(key1) - Bytes.getLong(key2));
      }
    });
    writeTransaction.commit();
    writeTransaction = env.createWriteTransaction();

    for (int i = 0; i < 1000; i++) {
      db.put(writeTransaction, Bytes.fromLong(i), Bytes.fromLong(i));
    }
    writeTransaction.commit();
    try (EntryIterator it = db.iterate(env.createReadTransaction()) ){
      long prev = -1;
      while(it.hasNext()) {
        if (prev == -1) {
          prev = Bytes.getLong(it.next().getKey());
        } else {
          long now = Bytes.getLong(it.next().getKey());
          assertThat((prev+1), is(now));
          prev = now;
        }
      }
    }
  }

  @Test
  public void testSetComparatorDesc() {
    if (Util.isAndroid() || Util.is32Bit()) {
      return;
    }
    Transaction writeTransaction = env.createWriteTransaction();
    db.setComparator(writeTransaction, new Comparator<byte[]>() {
      @Override
      public int compare(byte[] key1, byte[] key2) {
        return (int) (Bytes.getLong(key2) - Bytes.getLong(key1));
      }
    });
    writeTransaction.commit();
    writeTransaction = env.createWriteTransaction();

    for (int i = 0; i < 1000; i++) {
      db.put(writeTransaction, Bytes.fromLong(i), Bytes.fromLong(i));
    }
    writeTransaction.commit();
    try (EntryIterator it = db.iterate(env.createReadTransaction()) ){
      long prev = -1;
      while(it.hasNext()) {
        if (prev == -1) {
          prev = Bytes.getLong(it.next().getKey());
        } else {
          long now = Bytes.getLong(it.next().getKey());
          assertThat((prev-1), is(now));
          prev = now;
        }
      }
    }
  }


  @Test
  public void testSetDirectComparatorAsc() {
    if (Util.isAndroid() || Util.is32Bit()) {
      return;
    }
    Transaction writeTransaction = env.createWriteTransaction();
    db.setDirectComparator(writeTransaction, new Comparator<DirectBuffer>() {
      @Override
      public int compare(DirectBuffer key1, DirectBuffer key2) {
        return (int) (key1.getLong(0) - key2.getLong(0));
      }
    });
    writeTransaction.commit();
    writeTransaction = env.createWriteTransaction();

    for (int i = 0; i < 1000; i++) {
      db.put(writeTransaction, Bytes.fromLong(i), Bytes.fromLong(i));
    }
    writeTransaction.commit();
    try (EntryIterator it = db.iterate(env.createReadTransaction()) ){
      long prev = -1;
      while(it.hasNext()) {
        if (prev == -1) {
          prev = Bytes.getLong(it.next().getKey());
        } else {
          long now = Bytes.getLong(it.next().getKey());
          assertThat((prev+1), is(now));
          prev = now;
        }
      }
    }
  }

  @Test
  public void testSetDirectComparatorDesc() {
    if (Util.isAndroid() || Util.is32Bit()) {
      return;
    }
    Transaction writeTransaction = env.createWriteTransaction();
    db.setDirectComparator(writeTransaction, new Comparator<DirectBuffer>() {
      @Override
      public int compare(DirectBuffer key1, DirectBuffer key2) {
        return (int) (key2.getLong(0) - key1.getLong(0));
      }
    });
    writeTransaction.commit();
    writeTransaction = env.createWriteTransaction();

    for (int i = 0; i < 1000; i++) {
      db.put(writeTransaction, Bytes.fromLong(i), Bytes.fromLong(i));
    }
    writeTransaction.commit();
    try (EntryIterator it = db.iterate(env.createReadTransaction()) ){
      long prev = -1;
      while(it.hasNext()) {
        if (prev == -1) {
          prev = Bytes.getLong(it.next().getKey());
        } else {
          long now = Bytes.getLong(it.next().getKey());
          assertThat((prev-1), is(now));
          prev = now;
        }
      }
    }
  }
}
