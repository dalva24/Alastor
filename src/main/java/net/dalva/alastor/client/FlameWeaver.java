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
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.dalva.alastor.FileHandler;
import net.dalva.alastor.Tools;
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
  
  private static final ArrayList<ChunkTracker> chunks = new ArrayList();
  private static int activeServants = 0;
  private static int chunkSize = 0; // in BYTES
  private static long chunksLength = 0;
  private static FileHandler fh;
  private static int secondsWaited = 0;
  private static int lastSecondChunks = 0;

  /**
   * Proceed to download files
   *
   * @param clientParams
   * @throws InterruptedException
   */
  public static void weave(EntryClient clientParams) throws InterruptedException {

    params = clientParams;
    chunkSize = clientParams.getChunkSizeInBytes();

    //Get file information so we know how much chunks there is
    System.out.println("Getting file information of: " + params.getFilename());
    fileInfo = fileQuery();
    if (fileInfo.getError().getCode() != 0) { //error.
      System.out.println("Failure.");
      System.out.println("Error Code : " + fileInfo.getError().getCode());
      System.out.println("Error Msg  : " + fileInfo.getError().getMsg());
      return;
    }
    System.out.println("Success.");
    
    //Initialize chunks progress tracker
    chunksLength = fileInfo.getFileSize() / chunkSize;
    if (fileInfo.getFileSize()%chunkSize != 0) {
      chunksLength += 1; // if the last chunk will not fill the entire chunkSize allocation
    }
    for (int i=0; i<chunksLength; i++) {
      chunks.add(new ChunkTracker(i));
    }
    
    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS Z");
    System.out.println("Filename      : " + fileInfo.getFileName());
    System.out.println("Size (bytes)  : " + fileInfo.getFileSize());
    System.out.println("Last Modified : " + format.format(new Date(fileInfo.getFileTimestamp())));
    System.out.println("Total Chunks  : " + chunksLength);
    System.out.println("Last Imperfect chunk length is " + fileInfo.getFileSize()%chunkSize + " bytes");
    
    //Open the file for writing
    try {
      fh = new FileHandler(fileInfo.getFileName(), false);
    } catch (IOException ex) {
      System.err.println("IO error on file " + fileInfo.getFileName() + " - " + ex.getLocalizedMessage());
      return; //abort
    }

    //Print some inspirational quote just because.
    System.out.println("");
    System.out.println(CommandLine.Help.Ansi.AUTO.string(
            "@|fg(208) I, as the God of Destruction, will condemn them for their sins based on the rules I've set...|@"
            + "@|fg(243)  - Alastor (3E18-09:45)|@"));
    System.out.println("");

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
        secondsWaited++;
        System.out.println(getProgressInfo());
      }
      Thread.sleep(2000);
    } catch (InterruptedException ex) {
      threadPool.shutdownNow();
      Thread.currentThread().interrupt();
    }
    
    System.out.println("");
    System.out.println("@|fg(39) Download successful. |@");
    System.out.println("");

  }
  
  private static synchronized String getProgressInfo() {
    int speed = lastSecondChunks * chunkSize / 1024; //last second speed, in kBps
    lastSecondChunks = 0;
    
    int chunksGot = 0;
    for (ChunkTracker chunk : chunks) {
      if (chunk.getStatus() == ChunkTracker.STATUS.written) {
        chunksGot++;
      }
    }
    
    return String.format("%d s | %d kB/s | %d/%d chunks | %d active connections", secondsWaited, speed, chunksGot, chunksLength, activeServants);
  }

  /* ==============================================================================================================
   * Stuff that are going to be accessed by the servants are here
   */
  
  public static synchronized void notifyServantActive() {
    activeServants++;
  }
  
  public static synchronized void notifyServantDead() {
    activeServants--;
  }

  /**
   * See if there's still work to do To be used by the servants
   *
   * @return
   */
  public static synchronized ChunkTracker getNextReadyChunk() {
    for (ChunkTracker chunk : chunks) {
      if (chunk.getStatus() == ChunkTracker.STATUS.ready) {
        return chunk;
      }
    }
    return null;
  }

  /**
   * To be used by servants to submit completed chunks
   *
   * @param chunkData
   * @param chunkOffset
   * @throws java.io.IOException
   */
  public synchronized static void submitChunk(FileData chunkData, long chunkOffset) throws IOException {
    fh.writeOffset(chunkOffset*chunkSize, chunkData.getChunkData().toByteArray());
    lastSecondChunks++;
  }
  
  /**
   * Validate data based on their CRC 
   * 
   * @param fd
   * @return 
   */
  public static boolean validateData(FileData fd) {
    long fileSum = Tools.makeCRC32(fd.getChunkData().toByteArray());
    return fd.getChunkCrc32() == fileSum;
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
   * @param chunkOffset
   * @return
   */
  public static FileData dataQuery(AlastorBlockingStub stub, long chunkOffset) {
    DataQuery request = DataQuery.newBuilder()
            .setApiKey(params.getClientKey())
            .setRequestedFilename(params.getFilename())
            .setChunkSize(params.getChunkSizeInBytes())
            .setChunkOffset(chunkOffset)
            .build();
    FileData response;
    try {
      response = stub.getFileData(request);
      return response;
    } catch (StatusRuntimeException e) {
      System.err.printf("RPC failed: %s", e.getStatus());
      throw e;
    }
  }

}
