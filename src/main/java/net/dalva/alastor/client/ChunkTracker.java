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

/**
 *
 * @author Dalva
 */
public class ChunkTracker {
  
  private FlameServant handler = null;
  private boolean isDownloaded;
  private boolean isWritten;
  private final long chunkOffset;
  
  public static enum STATUS {ready,assigned,downloaded,written};
  
  public synchronized STATUS getStatus() {
    if (isWritten) {
      return STATUS.written;
    } else if (isDownloaded) {
      return STATUS.downloaded;
    } else if (handler != null) {
      return STATUS.assigned;
    } else {
      return STATUS.ready;
    }
  }
  
  public synchronized void assign(FlameServant handler) {
    this.handler = handler;
  }
  
  public synchronized void setDownloaded() {
    this.isDownloaded = true;
  }
  
  public synchronized void setWritten() {
    this.isWritten = true;
  }
  
  public synchronized void reset() {
    this.isWritten = false;
    this.isDownloaded = false;
    this.handler = null;
  }
  
  public long getOffset() {
    return chunkOffset;
  }
  
  public ChunkTracker(long chunkOffset) {
    this.chunkOffset=chunkOffset;
  }
  
  
}
