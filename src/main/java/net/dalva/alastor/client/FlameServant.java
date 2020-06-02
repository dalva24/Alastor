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

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import net.dalva.alastor.grpc.AlastorGrpc;
import net.dalva.alastor.grpc.FileData;
import picocli.CommandLine;

/**
 * This class is responsible for individual connection. To be used in a thread pool.
 *
 * @author Dalva
 */
public class FlameServant extends Thread {

  private final ManagedChannel channel;
  private final AlastorGrpc.AlastorBlockingStub blockingStub;
  
  private long monitorProcessedChunk = 0;
  private long monitorLastCheckedChunk = 0;
  private int monitorLoadingIndicator = 0;
  private boolean monitorThereWasAnError = false;
  private boolean finished = false;
  
  public String getLoadingIndicator() {
    if (monitorThereWasAnError) {
      monitorThereWasAnError = false;
      return CommandLine.Help.Ansi.AUTO.string("@|red #|@");
    } else if (finished) {
      return CommandLine.Help.Ansi.AUTO.string("@|cyan O|@");
    }
    if (monitorProcessedChunk != monitorLastCheckedChunk) {
      monitorLoadingIndicator = (monitorLoadingIndicator+1)%4;
      monitorLastCheckedChunk = monitorProcessedChunk;
    }
    switch (monitorLoadingIndicator) {
      case 0: return "|";
      case 1: return "/";
      case 2: return "-";
      case 3: return "\\";
      default: return "?";
    }
  }

  @Override
  public void run() {

    //System.out.println("Servant " + Thread.currentThread().getName() + " running");
    FlameWeaver.notifyServantActive();

    // The main loop
    while (true) {
      ChunkTracker nextChunk = getNextReadyChunk();
      if (nextChunk == null) { // no more chunks to download, kill servant.
        break;
      }

      FileData data;
      while (true) { // download loop until success
        try {
          data = FlameWeaver.dataQuery(blockingStub, nextChunk.getOffset());
          if (data.getError().getCode() == 0) {
            if (FlameWeaver.validateData(data)) {
              break;
            } else {
              InfoPrinter.printErrThreadSafe("CRC32 Error: chunk " + nextChunk.getOffset() + " retrying...");
              monitorThereWasAnError = true;
            }
          } else {
            InfoPrinter.printErrThreadSafe("Error: chunk " + nextChunk.getOffset() + " error " + data.getError().getCode() + " ; retrying...");
            monitorThereWasAnError = true;
          }
        } catch (StatusRuntimeException x) {
          if (x.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
            //no need to print out timeout errors.
            monitorThereWasAnError = true;
          }
        } catch (Exception ex) {
          InfoPrinter.printErrThreadSafe("Download error: chunk " + nextChunk.getOffset() + " retrying...");
          monitorThereWasAnError = true;
        }
      }
      nextChunk.setDownloaded();

      while (true) { // write loop until success
        try {
          FlameWeaver.submitChunk(data, nextChunk.getOffset());
          monitorProcessedChunk++;
          break;
        } catch (IOException ex) {
          System.err.println("Write error: chunk " + nextChunk.getOffset() + " retrying in 5 seconds...");
          monitorThereWasAnError = true;
          try {
            Thread.sleep(5000);
          } catch (InterruptedException ex1) {}
        }
      }

      nextChunk.setWritten();

    }

    //System.out.println("Servant " + Thread.currentThread().getName() + " has completed their services");
    FlameWeaver.notifyServantDead();
    finished = true;

    try {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException ex) {
      InfoPrinter.printErrThreadSafe(ex.getLocalizedMessage());
    }
  }

  private synchronized ChunkTracker getNextReadyChunk() {
    ChunkTracker nextChunk = FlameWeaver.getNextReadyChunk();
    if (nextChunk == null) {
      return null;
    }
    nextChunk.assign(this);
    return nextChunk;
  }

  /**
   * Construct the worker, initialize its own channel and stub.
   *
   * @param isNotTls
   * @param address
   */
  public FlameServant(boolean isNotTls, String address) {

    if (isNotTls) {
      channel = ManagedChannelBuilder.forTarget(address)
              .usePlaintext()
              .build();
    } else {
      channel = ManagedChannelBuilder.forTarget(address)
              .build();

    }
    blockingStub = AlastorGrpc.newBlockingStub(channel);
  }
}
