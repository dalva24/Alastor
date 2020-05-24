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
import io.grpc.StatusRuntimeException;
import java.text.SimpleDateFormat;
import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.dalva.alastor.grpc.AlastorGrpc;
import net.dalva.alastor.grpc.AlastorGrpc.AlastorBlockingStub;
import net.dalva.alastor.grpc.DataQuery;
import net.dalva.alastor.grpc.FileData;
import net.dalva.alastor.grpc.FileInfo;
import net.dalva.alastor.grpc.FileQuery;
import picocli.CommandLine;

/**
 * The main multi-connection controller and file assembler
 *
 * @author Dalva
 */
public class FlameWeaver {

  private static EntryClient params;
  private static ExecutorService threadPool;
  private static FileInfo fileInfo;

  /**
   * Proceed to download files
   *
   * @param clientParams
   * @throws InterruptedException
   */
  public static void weave(EntryClient clientParams) throws InterruptedException {

    params = clientParams;

    //Get file information so we know how much chunks there is
    System.out.println("Getting file information of: " + params.getFilename());
    
    fileInfo = fileQuery();
    if (fileInfo.getError().getCode() != 0) { //error.
      System.out.println("Failure.");
      System.out.println("Error Code : " + fileInfo.getError().getCode());
      System.out.println("Error Msg  : " + fileInfo.getError().getMsg());
      return;
    }
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS Z");
    System.out.println("Success.");
    System.out.println("Filename      : " + fileInfo.getFileName());
    System.out.println("Size (bytes)  : " + fileInfo.getFileSize());
    System.out.println("Last Modified : " + format.format(new Date(fileInfo.getFileTimestamp())));

    //Print some inspirational quote just because.
    System.out.println("");
    System.out.println(CommandLine.Help.Ansi.AUTO.string(
            "@|fg(208) I, as the God of Destruction, will condemn them for their sins based on the rules I've set...|@"
            + "@|fg(243)  - Alastor (3E18-09:45)|@"));
    System.out.println("");
    Thread.sleep(5000);

    //And may the deluge begins.
    int threads = params.getConns();
    threadPool = Executors.newFixedThreadPool(threads);
    for (int i = 0; i < threads; i++) {
      threadPool.submit(new FlameServant(params.isNotls(), params.getAddress()));
    }

    //Dont forget to shut down
    threadPool.shutdown();
    try {
      while (!threadPool.awaitTermination(1, TimeUnit.SECONDS)) {
        //continue waiting, TODO print status
        System.out.println("Awaited 1 seconds");
      }
      Thread.sleep(2000);
    } catch (InterruptedException ex) {
      threadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }

  }

  /* ==============================================================================================================
   * Stuff that are going to be accessed by the servants are here
   */
  
  private static BitSet chunksProcessing;
  private static BitSet chunksDone;
  
  //TODO make timer to re-clear chunksProcessing bits that times out, maybe after 20 seconds or so.
  //TODO better to think chunks as objects, no?
  
  /**
   * Get total chunks to be downloaded
   *
   * @return
   */
  public static long getTotalChunks() {
    return fileInfo.getFileSize() / (long) params.getChunkSizeInKB();
  }

  /**
   * See if there's still work to do To be used by the servants to control their deaths
   *
   * @return
   */
  public static int getNextChunk() {
    return chunksProcessing.nextClearBit(0);
  }

  /**
   * To be used by servants to submit completed chunks
   *
   * @param chunkData
   * @param chunk
   */
  public static void submitChunk(FileData chunkData, int chunk) {
    chunksDone.set(chunk);
    chunksProcessing.set(chunk);
    //TODO assemble the file here
  }

  /**
   * To be used by servants to inform chunk error.Chunks are then returned to unfinished stack
   *
   * @param chunk
   */
  public static void failChunk(int chunk) {
    chunksProcessing.clear(chunk);
  }

  /* ==============================================================================================================
   * gRPC Implementation methods here
   */
  /**
   * Query a file information from the server
   *
   */
  private static FileInfo fileQuery() throws InterruptedException {
    ManagedChannel queryChannel;

    //Check if we're operating on TLS
    if (params.isNotls()) {
      queryChannel = ManagedChannelBuilder.forTarget(params.getAddress())
              .usePlaintext()
              .build();
    } else {
      queryChannel = ManagedChannelBuilder.forTarget(params.getAddress())
              .build();

      //Then send the gRPC
    }
    try {
      AlastorBlockingStub blockingStub = AlastorGrpc.newBlockingStub(queryChannel);

      FileQuery request = FileQuery.newBuilder()
              .setApiKey(params.getClientKey())
              .setRequestedFilename(params.getFilename())
              .build();
      FileInfo response;

      try {
        response = blockingStub.getFileInfo(request);
      } catch (StatusRuntimeException e) {
        System.err.printf("RPC failed: %s", e.getStatus());
        throw e;
      }

      return response;
    } finally {
      queryChannel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

  }

  /**
   * Query the data, using worker's own stubs
   *
   * @param stub
   * @return
   */
  public static FileData dataQuery(AlastorBlockingStub stub, int chunkOffset) {
    DataQuery request = DataQuery.newBuilder()
            .setApiKey(params.getClientKey())
            .setRequestedFilename(params.getFilename())
            .setChunkSize(params.getChunkSizeInKB())
            .setChunkOffset(chunkOffset)
            .build();
    FileData response;
    try {
      response = stub.getFileData(request);
    } catch (StatusRuntimeException e) {
      System.err.printf("RPC failed: %s", e.getStatus());
      throw e;
    }
    return response;
  }

}
