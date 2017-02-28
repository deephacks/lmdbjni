# LMDB JNI

[![Build Status](https://travis-ci.org/deephacks/lmdbjni.png?branch=master)](https://travis-ci.org/deephacks/lmdbjni) [![Coverage Status](https://coveralls.io/repos/deephacks/lmdbjni/badge.svg?branch=master)](https://coveralls.io/r/deephacks/lmdbjni?branch=master) <a href="https://scan.coverity.com/projects/4017">
  <img alt="Coverity Scan Build Status"
       src="https://scan.coverity.com/projects/4017/badge.svg"/>
</a>

LMDB JNI provide a Java API to [LMDB](http://symas.com/mdb/) which is an ultra-fast, ultra-compact key-value embedded data store developed by Symas for the OpenLDAP Project. It uses memory-mapped files, so it has the read performance of a pure in-memory database while still offering the persistence of standard disk-based databases. Transactional with full ACID semantics and crash-proof by design. No corruption. No startup time. Zero-config cache tuning. No dependencies. Proven in production applications.

LMDB JNI is available for 64 bit Linux, OSX, Windows and Android.

### Documentation
 * [LMDB C API reference](http://lmdb.tech/doc/index.html)
 * [LMDB source code](https://github.com/LMDB/lmdb)
 * [LMDB JNI JavaDoc](http://deephacks.github.io/lmdbjni/)

### Presentations
 * [The Lightning Memory-mapped Database, Mar 02, 2016](http://www.infoq.com/presentations/lmdb-lighting-memory-mapped-database)
 * [Databaseology lecture series at Carnegie-Mellon University, Oct 08, 2015](http://cmudb.io/lectures2015-lmdb)
 * [LDAP at Lightning Speed, Jul 05, 2015](http://www.infoq.com/presentations/lmdb)
 * [The Lightning Memory-Mapped Database, Jun 24, 2013](https://www.parleys.com/play/517f58f9e4b0c6dcd95464ae/)

### Benchmarks

* [In-Memory Microbenchmark] (http://symas.com/mdb/inmem), June 2014

   Multithreaded read performance for a purely in-memory database.

* [In-Memory Microbenchmark (Scaling/NUMA)](http://symas.com/mdb/inmem/scale2/), September 2014
 
   Same as above showing performance improvements with  `numactl --interleave=all` enabled.

* [On-Disk Microbenchmark](http://symas.com/mdb/ondisk), November 2014

   Multithreaded read performance for a database that is over 5 times larger than the size of RAM.

* [RxLMDB benchmarks] (https://github.com/deephacks/RxLMDB), July 2015

   Benchmarks using [RxJava](https://github.com/ReactiveX/RxJava) and LMDB comparing zero copy, various serialization mechanisms, parallel and skip scans.

* [LMDB JNI Microbenchmark](http://pastebin.com/3huizUps), February 2015 ([source](https://github.com/deephacks/lmdbjni/blob/master/jmh/src/main/java/org/fusesource/lmdbjni/Iteration.java))
   
  Row scanning speed per second compared with the Java ports of [RocksDB](https://github.com/facebook/rocksdb), [LevelDB](https://github.com/dain/leveldb) and [MapDB](https://github.com/jankotek/MapDB).
   Mongodb is difficult to setup in JMH but [de.flapdoodle.embed.mongo](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) indicate that it is around 50x slower than lmdb_zero_copy.
   ```bash
  Benchmark                    Mode  Cnt         Score         Error  Units
  Iteration.leveldb           thrpt   10   6965637.351 ±  784589.894  ops/s
  Iteration.lmdb_buffer_copy  thrpt   10   3157796.643 ±  265830.424  ops/s
  Iteration.lmdb_zero_copy    thrpt   10  16372428.882 ± 1812316.504  ops/s
  Iteration.mapdb             thrpt   10   1358748.670 ±   87502.413  ops/s
  Iteration.rocksdb           thrpt   10   1311441.804 ±  176129.883  ops/s
   ```
* LMDB JNI microbenchmark, February 2016

  Random gets on a database with 370 million entries of 30GiB on a *non-SSD* drive. Keys 29 bytes and values 8 bytes. The target machine was busy serving traffic and this was the memory usage *before* executing the test.
  
  ```bash
  $ free -m
               total       used       free     shared    buffers     cached
  Mem:         32126      31890        235          0         55       9476
  -/+ buffers/cache:      22359       9767
  Swap:         7627       2350       5277

  ```
  Percentiles measured in nanoseconds using HdrHistogram.
  
  ```bash
   min       0.50        .90        0.99      0.999     0.9999        max
  5376      10367      12991      30335      51967      83967      83967
  4608      10175      12799      34559      84991     946175     946175
  3568      10239      12991      33791      70655     107007     107007
```

### Maven

*Windows, Android and OSX support has been discontinued in lmdbjni 0.4.7 (LMDB 0.9.19) and onward. Users can still build releases at their own convenience, but no artifacts will be published to Maven Central.*

*Please refer to [lmdbjava](https://github.com/lmdbjava/lmdbjava).*

```xml
<!-- required java classes -->

<dependency>
  <groupId>org.deephacks.lmdbjni</groupId>
  <artifactId>lmdbjni</artifactId>
  <version>${lmdbjni.version}</version>
</dependency>

<!-- prebuilt liblmdb platform packages -->

<dependency>
  <groupId>org.deephacks.lmdbjni</groupId>
  <artifactId>lmdbjni-linux64</artifactId>
  <version>${lmdbjni.version}</version>
</dependency>

<dependency>
  <groupId>org.deephacks.lmdbjni</groupId>
  <artifactId>lmdbjni-osx64</artifactId>
  <version>${lmdbjni.version}</version>
</dependency>

<dependency>
  <groupId>org.deephacks.lmdbjni</groupId>
  <artifactId>lmdbjni-win64</artifactId>
  <version>${lmdbjni.version}</version>
</dependency>

<!-- Android 5.0 (API level 21) 64-bit ARM -->
<dependency>
  <groupId>org.deephacks.lmdbjni</groupId>
  <artifactId>lmdbjni-android</artifactId>
  <version>${lmdbjni.version}</version>
</dependency>

```

### Usage

Recommended package imports.
```java
 import org.fusesource.lmdbjni.*;
 import static org.fusesource.lmdbjni.Constants.*;
```

[Opening](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/Env.html#open-java.lang.String-int-int-) and closing the database.
`Env` and `Database` shall be opened when the class is created (application, dao for example) and should be kept opened. 
 
```java
 try (Env env = new Env("/tmp/mydb")) {
   try (Database db = env.openDatabase()) {
     ... // use the db
   }
 }
```

[Putting](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/Database.html#put-org.fusesource.lmdbjni.Transaction-byte:A-byte:A-int-), [getting](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/Database.html#get-org.fusesource.lmdbjni.Transaction-byte:A-), and [deleting](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/Database.html#delete-org.fusesource.lmdbjni.Transaction-byte:A-byte:A-) key/values.
```java
 db.put(bytes("Tampa"), bytes("rocks"));
 String value = string(db.get(bytes("Tampa")));
 db.delete(bytes("Tampa"));
```

Iterating and seeking key/values forward and backward.

```java
Transaction tx = env.createReadTransaction();
try (EntryIterator it = db.iterate(tx)) {
  for (Entry next : it.iterable()) {
  }
}

try (EntryIterator it = db.iterateBackward(tx)) {
  for (Entry next : it.iterable()) {
  }
}

byte[] key = bytes("London");
try (EntryIterator it = db.seek(tx, key)) {
  for (Entry next : it.iterable()) {
  }
}

try (EntryIterator it = db.seekBackward(tx, key))) {
  for (Entry next : it.iterable()) {
  }
}
tx.abort();
```

Performing [transactional](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/Transaction.html) updates.

```java
 try (Transaction tx = env.createWriteTransaction()) {
   db.delete(tx, bytes("Denver"));
   db.put(tx, bytes("Tampa"), bytes("green"));
   db.put(tx, bytes("London"), bytes("red"));
   tx.commit();  // if commit is not called, the transaction is aborted
 }
```

Working against a snapshot view of the database using cursors.

```java
 // create a read-only transaction...
 try (Transaction tx = env.createReadTransaction()) {
   
   // All read operations will now use the same 
   // consistent view of the data.
   ... = db.openCursor(tx);
   ... = db.get(tx, bytes("Tampa"));
 }
```

A cursor in a write-transaction can be closed before its
transaction ends, and will otherwise be closed when its
transaction ends. A cursor must not be used after its transaction
is closed. Both these try blocks are ***unsafe*** and may SIGSEGV.

```java
 try (Transaction tx = env.createWriteTransaction();
      Cursor cursor = db.openCursor(tx)) {
   ...
   tx.commit();
 }

 try (Transaction tx = env.createWriteTransaction();
      EntryIterator it = db.iterate(tx)) {
   ...
   tx.commit();
 }
```
A safer approach would be: 

```java 
try (Transaction tx = env.createWriteTransaction()) {
    try (Cursor cursor = db.openCursor(tx)) {
       ...
    }
    tx.commit();
}

try (Transaction tx = env.createWriteTransaction()) {
    try (Cursor cursor = db.iterate(tx)) {
        ...
    }
    tx.commit();
}

```


A cursor in a read-only transaction must be closed explicitly,
before or after its transaction ends. Both these try blocks are safe.

```java
 try (Transaction tx = env.createReadTransaction();
      Cursor cursor = db.openCursor(tx)) {
 }

 try (Transaction tx = env.createReadTransaction();
      EntryIterator it = db.iterate(tx)) {
 }
```

Set a custom key comparison function for a database. 

```java
 db.setComparator(tx, new Comparator<byte[]>() {
      @Override
      public int compare(byte[] key1, byte[] key2) {
        // do compare
      }
    });
```

Atomic hot [backup](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/Env.html#copy-java.lang.String-).

```java
 env.copy(backupPath);
```

Using a memory pool to make native memory allocations more efficient:

```java
 Env.pushMemoryPool(1024 * 512);
 try {
     // .. work with the DB in here, 
 } finally {
     Env.popMemoryPool();
 }
```

### Zero copy usage

The safest (and least efficient) approach for interacting with LMDB JNI is using buffer copy as shown above. [BufferCursor](http://deephacks.org/lmdbjni/apidocs/org/fusesource/lmdbjni/BufferCursor.html) is a more efficient, zero copy mode. This mode is not available on Android.

*There are also methods that give access to DirectBuffer, but users should avoid interacting directly with these and use the BufferCursor API instead. Otherwise take extra care of buffer memory address+size and byte ordering. Mistakes may lead to SIGSEGV or unpredictable key ordering etc.*

```java
 // read only
 try (Transaction tx = env.createReadTransaction(); 
      BufferCursor cursor = db.bufferCursor(tx)) {
   // iterate from first item and forwards
   if (cursor.first()) {
     do {
       // read a position in buffer
       cursor.keyByte(0);
       cursor.valByte(0);
     } while (cursor.next());
   }

   // iterate from last item and backwards
   if (cursor.last()) {
     do {
       // copy entire buffer
       cursor.keyBytes();
       cursor.valBytes();
     } while (cursor.prev());
   }
   
   // find entry matching exactly the provided key
   cursor.keyWriteBytes(bytes("Paris"));
   if (cursor.seekKey()) {
     // read utf-8 string from position until NULL byte
     cursor.valUtf8(0);
   }

   // find first key greater than or equal to specified key.
   cursor.keyWriteBytes(bytes("London"));
   if (cursor.seekRange()) {
     // read utf-8 string from position until NULL byte
     cursor.keyUtf8(0);
     cursor.valUtf8(0);
   }
 }
 
 // open for write
 try (Transaction tx = env.createWriteTransaction()) {
   // cursors must close before write transactions!
   try (BufferCursor cursor = db.bufferCursor(tx)) {
     if (cursor.first()) {
       // write utf-8 ending with NULL byte
       cursor.keyWriteUtf8("England");
       cursor.valWriteUtf8("London");
       // overwrite existing item if any. Data is not written
       // into database before this operation is called and
       // no updates are visible outside this transaction until
       // the transaction is committed
       cursor.overwrite();
       cursor.first();
       // delete current cursor position
       cursor.delete();
     }
   }
   // commit changes or try-with-resources will auto-abort
   tx.commit();
 } 

```
### License

This project is licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) but the binary jar it produces also includes `liblmdb` library of the OpenLDAP project which is licensed under the [The OpenLDAP Public License](http://www.openldap.org/software/release/license.html).
