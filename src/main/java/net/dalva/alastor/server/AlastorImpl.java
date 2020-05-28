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

import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.dalva.alastor.Tools;
import net.dalva.alastor.grpc.AlastorGrpc;
import net.dalva.alastor.grpc.DataQuery;
import net.dalva.alastor.grpc.FileData;
import net.dalva.alastor.grpc.FileInfo;
import net.dalva.alastor.grpc.FileQuery;
import net.dalva.alastor.grpc.ErrorMsg;

/**
 * An implementation of the Server-Side logic for Alastor gRPC Protocol
 *
 * @author Dalva
 */
public class AlastorImpl extends AlastorGrpc.AlastorImplBase {

  /**
   * Get file information
   * Implemented.
   * @param request
   * @param responseObserver 
   */
  @Override
  public void getFileInfo(FileQuery request, StreamObserver<FileInfo> responseObserver) {

    if (!Auth.checkTrusted(request.getApiKey())) {
      System.err.println("A GetFileInfo request has been denied: client key untrusted:" + request.getApiKey());
      ErrorMsg errVal = ErrorMsg.newBuilder()
              .setCode(2)
              .setMsg("Unauthenticated")
              .build();
      responseObserver.onNext(FileInfo.newBuilder().setError(errVal).build());
      responseObserver.onCompleted();
      return;
    }
    
    try {
      ServerFileHandler fh = ServerFileHandler.get(request.getRequestedFilename(), true);
      
      responseObserver.onNext(fh.getFileInfo());
      responseObserver.onCompleted();
      return;
    } catch (IOException ex) {
      Logger.getLogger(AlastorImpl.class.getName()).log(Level.SEVERE, null, ex);
      ErrorMsg errVal = ErrorMsg.newBuilder()
              .setCode(10)
              .setMsg("General IO Error: " + ex.getLocalizedMessage())
              .build();
      responseObserver.onNext(FileInfo.newBuilder().setError(errVal).build());
      responseObserver.onCompleted();
      return;
    }
  }

  /**
   * Get File Data
   * Implemented.
   * @param request
   * @param responseObserver 
   */
  @Override
  public void getFileData(DataQuery request, StreamObserver<FileData> responseObserver) {
    

    if (!Auth.checkTrusted(request.getApiKey())) {
      System.err.println("A getFileData request has been denied: client key untrusted:" + request.getApiKey());
      ErrorMsg errVal = ErrorMsg.newBuilder()
              .setCode(2)
              .setMsg("Unauthenticated")
              .build();
      responseObserver.onNext(FileData.newBuilder().setError(errVal).build());
      responseObserver.onCompleted();
      return;
    }
    
    ErrorMsg errVal = ErrorMsg.newBuilder().setCode(0).build();
    try {
      ServerFileHandler fh = ServerFileHandler.get(request.getRequestedFilename(), true);
      //System.out.println("getting file " + request.getRequestedFilename() + " chunk offset " + request.getChunkOffset() + " length " + request.getChunkSize() + " bytes");
      byte[] data = fh.readOffsetChunk(request.getChunkOffset(), request.getChunkSize());
      long crc32 = Tools.makeCRC32(data);
      FileData reply = FileData.newBuilder()
            .setError(errVal)
            .setFileName(fh.getFileName())
            .setChunkData(ByteString.copyFrom(data))
            .setChunkCrc32(crc32)
            .build();
    responseObserver.onNext(reply);
    responseObserver.onCompleted();
    return;
    } catch (IOException ex) {
      Logger.getLogger(AlastorImpl.class.getName()).log(Level.SEVERE, null, ex);
      errVal = ErrorMsg.newBuilder()
            .setCode(10)
            .setMsg(ex.getLocalizedMessage())
            .build();
      responseObserver.onNext(FileData.newBuilder().setError(errVal).build());
      responseObserver.onCompleted();
      return;
    }
    
  }

}
