/* 
 *  ALASTOR Massively Concurrent File Transfer System
 *  Copyright (C) 2020 Dariel Valdano
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.dalva.alastor;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import net.dalva.alastor.grpc.ErrorMsg;
import net.dalva.alastor.grpc.FileInfo;

/**
 * Handles file operations relevant to Alastor
 *
 * @author Dalva
 */
public class FileHandler implements Closeable {

  protected final File file;
  protected final RandomAccessFile raFile;
  protected final FileChannel fileChannel;
  protected final FileLock fileLock;
  protected final boolean readOnly;
  protected final String filename;
  protected final int hashCode;
  
  public String getFileName() {
    return filename;
  }
  
  public int getHashCode() {
    return hashCode;
  }

  /**
   * Opens the specified file and obtain a lock
   *
   * @param fname file name to be opened
   * @param readOnly whether to make it Read-Only
   * @throws IOException
   */
  public FileHandler(String fname, boolean readOnly) throws IOException {
    file = new File(fname);
    System.out.println("Opening file: " + file.getAbsolutePath());
    this.filename = fname;
    this.readOnly = readOnly;
    hashCode = file.hashCode();
    if (readOnly) {
      raFile = new RandomAccessFile(file, "r");
      fileChannel = raFile.getChannel();
      fileLock = fileChannel.lock(0L, Long.MAX_VALUE, true);
    } else {
      raFile = new RandomAccessFile(file, "rw");
      fileChannel = raFile.getChannel();
      fileLock = fileChannel.lock(0L, Long.MAX_VALUE, false);
    }
  }

  /**
   * Releases the file lock and close it
   *
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    if (fileLock != null) {
      fileLock.release();
    }
    fileChannel.close();
    raFile.close();
    System.out.println("Closing unused file: " + file.getAbsolutePath());

  }

  /**
   * Perform a Random Read on an offset for specified length of bytes
   *
   * @param offset offset in bytes
   * @param length length to read in bytes
   * @return bytes that has been read
   * @throws IOException
   */
  public synchronized byte[] readOffset(long offset, int length) throws IOException {
    byte[] retval = new byte[length];
    raFile.seek(offset);
    raFile.readFully(retval, 0, length);
    return retval;
  }

  /**
   * Read a line of string. Useful for reading authorized keys in server-side implementation
   *
   * @return A line that has been read, without EOL characters
   * @throws IOException
   */
  public synchronized String readLine() throws IOException {
    return raFile.readLine();
  }
  
  /**
   * Perform a Random Write of the entire passed byte array to an offset
   *
   * @param offset offset in bytes
   * @param data data to be written
   * @throws IOException
   */
  public synchronized void writeOffset(long offset, byte[] data) throws IOException {
    if (readOnly) {
      throw new IOException("File is read only");
    }
    raFile.seek(offset);
    raFile.write(data);
  }

}
