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
import net.dalva.alastor.grpc.ErrorMsg;
import net.dalva.alastor.grpc.FileInfo;

/**
 *
 * @author Dalva
 */
public class ServerFileHandler extends net.dalva.alastor.FileHandler {
  
  
  //Collects the list of open files
  private static ArrayList<ServerFileHandler> openedFiles = new ArrayList();
  
  //path prefix
  private static String prefix;
  
  static {
    //TODO make files auto-close and remove from openedFile
  }
  
  private Date lastAccessed;
  
  public static void setPrefix(String newPrefix) {
    prefix = newPrefix;
  }
  
  
  /**
   * Get a ServerFileHandler for the specified fname
   * Will first check whether we have it open before, else open the file
   * @param fname
   * @param readOnly
   * @return The file handler
   * @throws IOException 
   */
  public static ServerFileHandler get(String fname, boolean readOnly) throws IOException {
    int hashCode = new File(prefix+fname).hashCode();
    
    //Check first to see if we have already opened the file before
    for (ServerFileHandler fh : openedFiles) {
      if (hashCode == fh.getHashCode()) {
        return fh;
      }
    }
    
    //If not, then open the file
    return new ServerFileHandler(prefix+fname, readOnly);
    
  }
  
  /**
   * Opens the specified file and obtain a lock
   * And adds it to the opened files list
   * @param fname file name to be opened
   * @param readOnly whether to make it Read-Only
   * @throws IOException
   */
  private ServerFileHandler(String fname, boolean readOnly) throws IOException {
    super(fname, readOnly);
    lastAccessed = new Date();
    openedFiles.add(this);
    System.out.println("Opened new file: " + fname);
  }
  
  /**
   * Releases the file lock and close it
   * Additionally remove it from the server's opened files list
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    super.close();
    openedFiles.remove(this);
  }

  /**
   * Perform a Random Read on an offset for specified length of bytes
   * Additionally touches the file's last modification date
   * @param offset offset in bytes
   * @param length length to read in bytes
   * @return bytes that has been read
   * @throws IOException
   */
  @Override
  public byte[] readOffset(int offset, int length) throws IOException {
    lastAccessed = new Date();
    return super.readOffset(offset, length);
  }

  /**
   * Read a line of string. Useful for reading authorized keys in server-side implementation
   * Additionally touches the file's last modification date
   * @return A line that has been read, without EOL characters
   * @throws IOException
   */
  @Override
  public String readLine() throws IOException {
    lastAccessed = new Date();
    return super.readLine();
  }
  
  /**
   * Perform a Random Write of the entire passed byte array to an offset
   * Additionally touches the file's last modification date
   * @param offset offset in bytes
   * @param data data to be written
   * @throws IOException
   */
  public void writeOffset(int offset, byte[] data) throws IOException {
    lastAccessed = new Date();
    super.writeOffset(offset, data);
  }

  /**
   * Get file information
   * @return 
   */
  public FileInfo getFileInfo() {
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
