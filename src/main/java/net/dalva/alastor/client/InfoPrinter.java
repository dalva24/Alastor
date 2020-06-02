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
package net.dalva.alastor.client;

import java.text.SimpleDateFormat;
import java.util.Date;
import net.dalva.alastor.Main;
import picocli.CommandLine;
import picocli.CommandLine.Help.Ansi;

/**
 *
 * @author Dalva
 */
public class InfoPrinter {

  private static long startTimestamp = new Date().getTime();
  
  private static final Object PRINT_MUTEX = new Object();

  private static int[] lastChnkDl = {0, 0, 0, 0, 0};
  private static int lastIdx = 0;

  /**
   * Update last second chunk download amount
   *
   * @param chunksDownloaded
   */
  public static void updateLastSecond(int chunksDownloaded) {
    lastChnkDl[lastIdx] = chunksDownloaded;
    lastIdx = (lastIdx+1)%5;
  }

  /**
   * averages last 5 seconds of downloaded chunks count
   *
   * @return
   */
  public static int getAverageChunks() {
    int avgSpeed = 0;
    for (int last : lastChnkDl) {
      avgSpeed += last;
    }
    return avgSpeed / 5;
  }

  /**
   * Print Download Information
   *
   * @param notTLS
   * @param addressPort
   * @param fname
   * @param ModDate
   * @param totSizeBytes
   * @param dlChunk
   * @param totChunk
   * @param chunkSizeBytes
   * @param activeConn
   * @param maxConn
   */
  public static void printDlInfo(
          boolean notTLS,
          String addressPort,
          String fname,
          long ModDate,
          long totSizeBytes,
          long dlChunk,
          long totChunk,
          int chunkSizeBytes,
          int activeConn,
          int maxConn) {
    
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS Z");
    StringBuilder sb = new StringBuilder();
    sb.append(String.format("\033[H\033[2J"));
    sb.append(String.format("ALASTOR CLIENT %s   ================================\n", Main.VERSION));
    if (notTLS) {
      sb.append(String.format("Connection      : %s %s\n", Ansi.AUTO.string("@|red plain|@"), addressPort));
    } else {
      sb.append(String.format("Connection      : %s %s\n", Ansi.AUTO.string("@|cyan TLS|@"), addressPort));
    }
    sb.append(String.format("Downloading     : %s\n", fname));
    sb.append(String.format("Last Modified   : %s\n", format.format(ModDate)));
    double percentComplete = ((double)dlChunk/(double)totChunk * 100);
    long dlSizeMB = (dlChunk-1)*chunkSizeBytes/1024/1024;
    long totSizeMB = totSizeBytes/1024/1024;
    sb.append(String.format("Size (DL/Tot)   : %d/%d MB (%s)\n", 
            dlSizeMB, 
            totSizeMB, 
            CommandLine.Help.Ansi.AUTO.string("@|cyan " + String.format("%.2f%%", percentComplete) + "|@")));
    sb.append(String.format("Chunks (DL/Tot) : %d/%d @ %d kB each\n", dlChunk, totChunk, chunkSizeBytes/1024));
    int dlSpeedKBps = getAverageChunks() * chunkSizeBytes / 1024;
    sb.append(String.format("Speed (5s avg)  : %d kB/s\n", dlSpeedKBps));
    if (dlSpeedKBps == 0) {
      sb.append(String.format("Elapsed / ETA   : %s / inf\n", getElapsedTime()));
    } else {
      long etaSeconds = (totSizeMB - dlSizeMB) * 1024 / dlSpeedKBps;
      sb.append(String.format("Elapsed / ETA   : %s / %s\n", getElapsedTime(), convertToHHMMSS(etaSeconds)));
    }
    sb.append(String.format("------------------------------------------------------\n"));
    sb.append(String.format("Connections : %d/%d (active/total)", activeConn, maxConn));
    sb.append(String.format(FlameWeaver.getServantInfo()));
    sb.append(String.format("======================================================\n"));
    synchronized (PRINT_MUTEX) {
      System.out.println(sb.toString());
      System.out.flush();
    }
  }
  
  public static void printErrThreadSafe(String msg) {
    synchronized (PRINT_MUTEX) {
      System.err.println(msg);
    }
  }

  public static void resetTimer() {
    startTimestamp = new Date().getTime();
  }

  private static String getElapsedTime() {
    int seconds = (int) (new Date().getTime() - startTimestamp) / 1000;
    return convertToHHMMSS(seconds);
  }

  private static String convertToHHMMSS(long seconds) {
    return String.format("%02d:%02d:%02d",
            seconds / 3600,
            (seconds % 3600) / 60,
            seconds % 60);
  }

}
