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
package net.dalva.alastor.server;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.dalva.alastor.Tools;
import net.dalva.alastor.grpc.ErrorMsg;
import net.dalva.alastor.grpc.FileInfo;

/**
 *
 * @author Dalva
 */
public class ServerFileHandler extends net.dalva.alastor.FileHandler {

  //Collects the list of open files
  private static final ArrayList<ServerFileHandler> OPENED_FILES = new ArrayList();
  private static final ScheduledExecutorService FILE_CLOSER = Executors.newSingleThreadScheduledExecutor();
  private static final Runnable CLOSER_METHOD = () -> {
    System.out.println("Opened files: " + OPENED_FILES.size());
    for (ServerFileHandler file : OPENED_FILES) {
      Date now = new Date();
      long diffMillis = Math.abs(now.getTime() - file.getLastAccessed().getTime());
      long diff = TimeUnit.SECONDS.convert(diffMillis, TimeUnit.MILLISECONDS);
      if (diff > 5) {
        try {
          file.close();
        } catch (IOException ex) {
          System.err.println("Error closing file: " + ex.getLocalizedMessage());
        }
      }
    }
  };

  //path prefix
  private static String prefix;

  static { // TODO find out why this stops after removing first file. For now it is disabled.
    //FILE_CLOSER.scheduleAtFixedRate(CLOSER_METHOD, 0, 1, TimeUnit.SECONDS);
  }

  private Date lastAccessed;

  public static ArrayList<ServerFileHandler> getOpenedFiles() {
    return OPENED_FILES;
  }

  public static void setPrefix(String newPrefix) {
    prefix = newPrefix;
  }

  /**
   * Get a ServerFileHandler for the specified fname Will first check whether we have it open before, else open the file
   *
   * @param fname
   * @param readOnly
   * @return The file handler
   * @throws IOException
   */
  public synchronized static ServerFileHandler get(String fname, boolean readOnly) throws IOException {
    fname = Tools.sanitizePath(fname);
    File fileToOpen = new File(prefix + fname);
    File prefixPath = new File(prefix);
    if ( ! fileToOpen.getCanonicalPath().startsWith(prefixPath.getCanonicalPath())) {
      //possible directory traversal attack
      throw new IOException("Cannot open files outside serve dir");
    }
    int hashCode = fileToOpen.hashCode();

    //Check first to see if we have already opened the file before
    for (ServerFileHandler fh : OPENED_FILES) {
      if (hashCode == fh.getHashCode()) {
        return fh;
      }
    }

    //If not, then open the file
    return new ServerFileHandler(prefix + fname, readOnly);

  }

  /**
   * Opens the specified file and obtain a lock And adds it to the opened files list
   *
   * @param fname file name to be opened
   * @param readOnly whether to make it Read-Only
   * @throws IOException
   */
  private ServerFileHandler(String fname, boolean readOnly) throws IOException {
    super(fname, readOnly);
    lastAccessed = new Date();
    OPENED_FILES.add(this);
    System.out.println("Opened new file: " + fname);
  }

  /**
   * Releases the file lock and close it Additionally remove it from the server's opened files list
   *
   * @throws IOException
   */
  @Override
  public synchronized void close() throws IOException {
    super.close();
    OPENED_FILES.remove(this);
  }

  public synchronized Date getLastAccessed() {
    return lastAccessed;
  }

  /**
   * Perform a Random Read on a specific chunk offset Additionally touches the file's last modification date
   *
   * @param chunkOffset chunk index to read
   * @param chunkSize size of each chunk
   * @return bytes that has been read
   * @throws IOException
   */
  public synchronized byte[] readOffsetChunk(long chunkOffset, int chunkSize) throws IOException {
    lastAccessed = new Date();
    //Check first if we're getting the last chunk that are not perfectly chunkLength-sized
    long totalChunks = file.length() / chunkSize;
    boolean imperfectChunkExists = file.length() % chunkSize != 0;
    if (chunkOffset < totalChunks) {
      //continue as normal
      //System.out.println("reading chunk " + (chunkOffset+1) + " out of " + totalChunks + " chunks");
      return super.readOffset(chunkSize * chunkOffset, chunkSize);
    } else if (chunkOffset == totalChunks) {
      if (imperfectChunkExists) {
        //we're getting the last chunk which size is lower than chunkSize
        double lastChunkSize = file.length() % chunkSize;
        //System.out.println("reading last chunk " + (chunkOffset+1) + " with size " + lastChunkSize);
        return super.readOffset(chunkSize * chunkOffset, (int) lastChunkSize);
      } else {
        //continue as normal
        //System.out.println("reading last chunk " + (chunkOffset+1) + " out of " + totalChunks + " chunks");
        return super.readOffset(chunkSize * chunkOffset, chunkSize);
      }

    } else {
      throw new IOException("Attempting to read chunks outside file range");
    }
  }

  /**
   * Dont use this in ServerFileHandler
   *
   * @param offset offset in bytes
   * @param length length to read in bytes
   * @return bytes that has been read
   * @throws IOException
   * @deprecated
   */
  @Deprecated
  @Override
  public synchronized byte[] readOffset(long offset, int length) throws IOException {
    throw new IOException("Dont use readOffset in ServerFileHandler");
  }

  /**
   * Read a line of string. Useful for reading authorized keys in server-side implementation Additionally touches the file's last modification date
   *
   * @return A line that has been read, without EOL characters
   * @throws IOException
   */
  @Override
  public synchronized String readLine() throws IOException {
    lastAccessed = new Date();
    return super.readLine();
  }

  /**
   * Perform a Random Write of the entire passed byte array to an offset Additionally touches the file's last modification date
   *
   * @param offset offset in bytes
   * @param data data to be written
   * @throws IOException
   */
  public synchronized void writeOffset(int offset, byte[] data) throws IOException {
    lastAccessed = new Date();
    super.writeOffset(offset, data);
  }

  /**
   * Get file information
   *
   * @return
   */
  public synchronized FileInfo getFileInfo() {
    lastAccessed = new Date();
    if (file.isFile()) {
      return FileInfo.newBuilder()
              .setError(ErrorMsg.newBuilder().setCode(0).build())
              .setFileName(file.getName())
              .setFileSize(file.length())
              .setFileTimestamp(file.lastModified())
              .build();
    } else {
      return FileInfo.newBuilder()
              .setError(
                      ErrorMsg.newBuilder()
                              .setCode(11)
                              .setMsg("File " + filename + " found")
                              .build())
              .build();
    }
  }

}
