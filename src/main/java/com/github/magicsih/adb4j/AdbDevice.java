/**
 * 
 */
package com.github.magicsih.adb4j;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import com.github.magicsih.adb4j.listener.AdbPollingCommand;

/**
 * @author sih
 *
 */
public class AdbDevice implements AutoCloseable {

  private static final Logger LOG = Logger.getLogger(AdbDevice.class.getName());
  
  private AsynchronousSocketChannel client; 
  private final AsynchronousChannelGroup channelGroup;

  private final String deviceId;
  private final ExecutorService postWorker;

  private final SocketAddress addr;

  private String shellHeader;

  private final Semaphore readSemaphore;
  private final Semaphore writeSemaphore;

  private Map<String, AdbPollingCommand> pollingCommandMap;
  
  private String packageName;
  private String userId;
  private String pid;

  AdbDevice(String deviceId, AsynchronousChannelGroup asynchronousChannelGroup, SocketAddress addr, ExecutorService postWorker) throws IOException {
    super();
    this.deviceId = deviceId;
    this.channelGroup = asynchronousChannelGroup;
    this.addr = addr;
    this.postWorker = postWorker;
    this.pollingCommandMap = new HashMap<>();
    this.readSemaphore = new Semaphore(1);
    this.writeSemaphore = new Semaphore(1);
  }

  public String getDeviceId() {
    return deviceId;
  }
  
  public void unregisterPollingListener(String name) {
    String key = deviceId + ":" + name;
    AdbPollingCommand command = this.pollingCommandMap.remove(key);
    if(command != null) {
      command.clearListener();
    }
  }

  public void registerPollingListener(AdbPollingCommand pollingCommand) {
    String key = deviceId + ":" + pollingCommand.getName();
    if(this.pollingCommandMap.containsKey(key)) {
      throw new RuntimeException("listener " + key + " is already registered.");
    }
    this.pollingCommandMap.put(key, pollingCommand);
  }

  @Override
  public void close() throws Exception {
    if(this.client == null) return;    

    if(this.client.isOpen()) {
      LOG.info("Closing ADPC Device(" + deviceId + ")");
      client.shutdownInput();
      client.shutdownOutput();    
      client.close();
    }
  }  

  private void closeGracefully(ChannelContext ctx) {
    try {      
      log(Level.WARNING, "closing...");
      close();
      readSemaphore.release();
      writeSemaphore.release();


    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if(ctx != null) {
        ctx.getListenerHolder().getListener().disconnect(this);
        //        ctx.getListenerHolder().clearListener();        
      }
    }
  }

  /**
   * Start connecting to adb device synchronosuly-
   * @param packageName
   */
  public void start(String packageName) {
    if(this.client != null && this.client.isOpen()) {
      throw new RuntimeException("Channel already started");
    }

    //    this.pollingListnerMap = Collections.unmodifiableMap(this.pollingListnerMap);

    try {
      this.client = AsynchronousSocketChannel.open(this.channelGroup);
    } catch (IOException e) {      
      log(Level.WARNING, " start failed! " + e.getMessage());
      return;
    }

    ChannelContext context = new ChannelContext(this, this.client);
    Future<Void> connect = client.connect(addr);
    try {
      connect.get();
      log(Level.INFO, "connected to adb host!");
      
      log(Level.INFO, "transporting to local device...");
      writeSynchronouslyForAdb(context, "host:transport:" + deviceId);
      byte[] okay = readSynchronouslyForExactSize(context,4);
      assert(Arrays.equals(AdbChannelMessage.OKAY.getPayload(), okay));

      log(Level.INFO, "starting shell...");
      writeSynchronouslyForAdb(context, "shell:");
      okay = readSynchronouslyForExactSize(context,4);
      assert(Arrays.equals(AdbChannelMessage.OKAY.getPayload(), okay));

      shellHeader = consumeShellHeaderSynchronously(context);
      log(Level.INFO, "shell started! shell header:" + shellHeader);          

      writeSynchronously(context, "dumpsys package " + packageName + " | grep 'userId=' | sed 's/[^0-9]*//g'\n");
      String rawUserId = readUntilShellHeader(context);
      String[] split = rawUserId.split("\n");
      if(split.length < 3) throw new RuntimeException("There is no userId for packageName : " + packageName);
      this.userId = split[1].replaceAll("[^0-9]+","");
      log(Level.INFO, packageName + "'s userId=" + userId);
      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void startPolling() {
    pollingCommandMap.forEach((k,h)-> {
      log(Level.INFO, " polling with command : " + h.getCommand());
      ChannelContext ctx = new ChannelContext(this, this.client, h);
      pollFromAdbShell(ctx);
    });
  }

  private void pollFromAdbShell(ChannelContext ctx) {
    try {
      writeSemaphore.acquire(); // only one thread can write to the channel
    } catch (InterruptedException e) {
      log(Level.WARNING, e.getMessage());      
      closeGracefully(ctx);
      return;
    }

    if(!ctx.getChannel().isOpen()) return;

    try {
      ctx.getChannel().write(ctx.getWriteBuffer(), ctx, new CompletionHandler<Integer, ChannelContext>() {

        @Override
        public void completed(Integer result, ChannelContext ctx) {
          log(Level.FINE, " WRITE " + result + " BYTES (pollFromAdbShell - async write)");

          if(result < 0) {
            closeGracefully(ctx);
            return;
          }
          

          ctx.getWriteBuffer().rewind();
          readPollingDataFromAdbShell(ctx);
        }

        @Override
        public void failed(Throwable exc, ChannelContext ctx) {
          writeSemaphore.release(); // this thread finish its own read operation.
          log(Level.WARNING, exc.getMessage());      
          closeGracefully(ctx);
        }});
    } catch(Exception ex) {
      log(Level.WARNING, ex.getMessage());      
    }
  }

  private void readPollingDataFromAdbShell(ChannelContext ctx) {
    try {
      readSemaphore.acquire();  // only one thread can read from the channel
    } catch (InterruptedException e) {      
      log(Level.WARNING, e.getMessage());
      closeGracefully(ctx);
      return;
    }

    if(!ctx.getChannel().isOpen()) return;

    ctx.getChannel().read(ctx.getReadBuffer(), ctx, new CompletionHandler<Integer, ChannelContext>() {

      @Override
      public void completed(Integer result, final ChannelContext ctx) {
        log(Level.FINE, " READ " + result + " BYTES (readPollingDataFromAdbShell - async read)");

        if(result < 0) {
          closeGracefully(ctx);
          return;
        }

        int read = 0;
        final int shellHeaderLength = shellHeader.length();
        try {

          if(ctx.getListenerHolder().isKeepAlive()) {
            // If it's a keep-alive command. should read once and return.
            writeSemaphore.release();
            
            readSemaphore.release();
            byte[] bytesFromByteBuffer = ctx.clearAndGetBytesFromReadBuffer();

            String data = new String(bytesFromByteBuffer);

            log(Level.FINER, " gets new data :" + data);

            if(!data.startsWith((ctx.getListenerHolder().getCommand().substring(0, ctx.getListenerHolder().getCommand().length()-1)))) {
              postWorker.execute(()->{
                ctx.getListenerHolder().getListener().poll(ctx.getAdbDevice(), ctx.getListenerHolder().getName(), ctx.getListenerHolder().getCommand(), data);            
              });
            } else {
              System.err.println("something");
            }

            readPollingDataFromAdbShell(ctx);

          } else {
            // If it's not a keep-alive command. should read until header shown up.
            do {
              read = ctx.getChannel().read(ctx.getReadBuffer()).get();
              log(Level.FINE, " READ " + read + " BYTES (readPollingDataFromAdbShell - inside sync read)");

              if(read < 0) {
                closeGracefully(ctx);
                return;
              }
              ctx.flushReadBufferToReadMemory();
              if(ctx.lengthOfReadMemory() >= shellHeaderLength) {
                byte[] lastBytesFromReadMemory = ctx.getLastBytesFromReadMemory(shellHeaderLength);
                if(Arrays.equals(shellHeader.getBytes(), lastBytesFromReadMemory)) {
                  break;
                }
              }
            } while(read > 0);

            writeSemaphore.release(); // let the new write operation begin write operation should trigger right after read operation finished
            
            readSemaphore.release(); // let the new read operaion begin

            final byte[] bytesFromByteBuffer = ctx.clearAndGetBytesFromReadMemory();
            final String data = new String(bytesFromByteBuffer);

            postWorker.execute(()->{
              log(Level.FINER, "DATA:" + ctx.getListenerHolder().getCommand() + " , " + data);
              ctx.getListenerHolder().getListener().poll(ctx.getAdbDevice(), ctx.getListenerHolder().getName(), ctx.getListenerHolder().getCommand(), data);            
            });

            //          Thread.sleep(1000); // sleep 1s
            pollFromAdbShell(ctx);
          }

        } catch (InterruptedException | ExecutionException  e1) {
          readSemaphore.release(); // this thread finish its own read operation.
          e1.printStackTrace();
          closeGracefully(ctx);
        }
      }

      @Override
      public void failed(Throwable exc, ChannelContext ctx) {
        readSemaphore.release(); // this thread finish its own read operation.
        exc.printStackTrace();
        closeGracefully(ctx);
      }});
  }


  /**
   * this write method works synchronously on calling thread.
   * @param ctx
   * @param message
   * @throws ExecutionException 
   * @throws InterruptedException 
   */
  private void writeSynchronouslyForAdb(ChannelContext ctx, String message) throws InterruptedException, ExecutionException {
    ctx.fillWriteBufferForAdb(message);
    writeSynchronously(ctx);
  }
  
  /**
   * this write method works synchronously on calling thread.
   * @param ctx
   * @param message
   * @throws ExecutionException 
   * @throws InterruptedException 
   */
  private void writeSynchronously(ChannelContext ctx, String message) throws InterruptedException, ExecutionException {
    ctx.fillWriteBuffer(message);
    writeSynchronously(ctx);
  }
  
  /**
  * this write method works synchronously on calling thread.
  * @param ctx
  * @param message
  * @throws ExecutionException 
  * @throws InterruptedException 
  */
 private void writeSynchronously(ChannelContext ctx) throws InterruptedException, ExecutionException {
   Future<Integer> f = ctx.getChannel().write(ctx.getWriteBuffer());
   Integer written = f.get();
   log(Level.FINE, " WRITE " + written + " BYTES (writeSynchronously)");
   if(written < 0) {
     closeGracefully(ctx);
     return;
   }
   log(Level.FINE, " write synchronously " + written + " bytes");
   ctx.getWriteBuffer().clear();
 }

  /**
   * TODO How to assure if it's the end of shell start header  
   * @param ctx
   * @throws Exception
   */
  private String consumeShellHeaderSynchronously(ChannelContext ctx) throws Exception {
    Integer read = ctx.getChannel().read(ctx.getReadBuffer()).get();
    log(Level.FINE, " READ " + read + " BYTES (consumeShellHeaderSynchronously)");
    if(read < 0) {
      closeGracefully(ctx);
      return null;
    }
    ctx.getReadBuffer().flip();
    int limit = ctx.getReadBuffer().limit();
    byte bytes[] = new byte[limit];
    ctx.getReadBuffer().get(bytes, 0, limit);
    ctx.getReadBuffer().clear();    
    String shellHeader = new String(bytes);

    log(Level.FINE, " consume shell header synchronously " + read + " bytes - " + shellHeader);

    return shellHeader;
  }
  
  private String readUntilShellHeader(ChannelContext ctx) throws Exception {
    int read = 0;
    do {
      read = ctx.getChannel().read(ctx.getReadBuffer()).get();
      log(Level.FINE, " READ " + read + " BYTES (readPollingDataFromAdbShell - inside sync read)");

      if(read < 0) {
        closeGracefully(ctx);
        return "";
      }
      ctx.flushReadBufferToReadMemory();
      if(ctx.lengthOfReadMemory() >= shellHeader.length()) {
        byte[] lastBytesFromReadMemory = ctx.getLastBytesFromReadMemory(shellHeader.length());
        if(Arrays.equals(shellHeader.getBytes(), lastBytesFromReadMemory)) {
          break;
        }
      }
    } while(read > 0);
    
    final byte[] bytesFromByteBuffer = ctx.clearAndGetBytesFromReadMemory();
    return new String(bytesFromByteBuffer);
  }

  private byte[] readSynchronouslyForExactSize(ChannelContext ctx, final int size) throws Exception {    
    int totalRead = 0;
    while(size - totalRead > 0) {
      Integer read = ctx.getChannel().read(ctx.getReadBuffer()).get();
      log(Level.FINE, " READ " + read + " BYTES (readSynchronouslyForExactSize)");
      if(read < 0) {
        this.closeGracefully(ctx);
        return null;
      }
      totalRead += read;
    }


    ctx.getReadBuffer().flip();
    int limit = ctx.getReadBuffer().limit();
    byte bytes[] = new byte[limit];

    ctx.getReadBuffer().get(bytes, 0, limit);
    ctx.getReadBuffer().clear();

    if(totalRead == size) {
      log(Level.FINE, " read synchronously exact size " + totalRead + " bytes");
    } else {
      log(Level.WARNING, " try reading synchronously exact size but different - expected: " + size + " actual:" + totalRead + " msg:" + new String(bytes) + " hex:" + byteArrayToHex(bytes));
    }

    return bytes;
  }

  private String byteArrayToHex(byte[] a) {
    StringBuilder sb = new StringBuilder();
    for(final byte b: a)
      sb.append(String.format("%02x ", b&0xff));
    return sb.toString();
  }

  private void log(Level level, String msg) {
    if(LOG.isLoggable(level)) {
      LOG.log(level, "AdbDevice(" + deviceId + ") " + msg);
    }
  }
}
