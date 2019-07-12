/**
 * 
 */
package com.github.magicsih.adb4j;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.github.magicsih.adb4j.listener.AdbPollingCommand;

/**
 * @author sih
 *
 */
public class ChannelContext {
  
  private static final Logger LOG = Logger.getLogger(ChannelContext.class.getName());

  public static final int BUFFER_SIZE = 1024 * 512;
  
  private final AdbDevice adbDevice;
  private final AsynchronousSocketChannel channel; 
  private final ByteBuffer readBuffer;
  private final ByteBuffer writeBuffer;
  private final ByteBuffer readMemory; // to Read a sequence of bytes from this channel into a subsequence of the given buffers
  private final AdbPollingCommand listenerHolder;
  
  public ChannelContext(AdbDevice adbDevice, AsynchronousSocketChannel channel) {
    super();
    this.adbDevice = adbDevice;
    this.channel = channel;
    this.readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    this.writeBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    this.readMemory = ByteBuffer.allocate(BUFFER_SIZE * 2);
    this.listenerHolder = null;
  }
  
  public ChannelContext(AdbDevice adbDevice, AsynchronousSocketChannel channel, AdbPollingCommand listenerHolder) {
    super();
    this.adbDevice = adbDevice;
    this.channel = channel;
    this.readBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    this.writeBuffer = ByteBuffer.allocateDirect(listenerHolder.getCommand().length()).put(listenerHolder.getCommand().getBytes());
    this.writeBuffer.flip();
    this.readMemory = ByteBuffer.allocate(BUFFER_SIZE * 2);
    this.listenerHolder = listenerHolder;
  }

  public AsynchronousSocketChannel getChannel() {
    return channel;
  }

  public ByteBuffer getReadBuffer() {
    return readBuffer;
  }

  public ByteBuffer getWriteBuffer() {
    return writeBuffer;
  }
  
  public ByteBuffer getReadMemory() {
    return readMemory;
  }

  public AdbDevice getAdbDevice() {
    return adbDevice;
  }

  public void flushReadBufferToReadMemory() {
    this.readBuffer.flip();
    int l = this.readBuffer.limit();
    byte b[] = new byte[l];
    this.readBuffer.get(b,0,l);
    debug(b);
    this.readMemory.put(b);
    this.readBuffer.clear();
    
  }
  
  public byte[] clearAndGetBytesFromReadBuffer() {
    this.readBuffer.flip();
    int l = this.readBuffer.limit();
    byte b[] = new byte[l];
    this.readBuffer.get(b, 0, l);
    this.readBuffer.clear();
    debug(b);
    return b;
  }
  
  public byte[] clearAndGetBytesFromReadMemory() {
    this.readMemory.flip();
    int l = this.readMemory.limit();
    byte b[] = new byte[l];
    this.readMemory.get(b, 0, l);
    this.readMemory.clear();
    debug(b);
    return b;
  }
  
  public int lengthOfReadMemory() {
    return this.readMemory.position();
  }
  
  public byte[] getLastBytesFromReadMemory(final int lengthFromEnd) {
    final int position = this.readMemory.position();
    final int offset = position - lengthFromEnd;
    this.readMemory.position(offset);
    
    byte[] b = new byte[lengthFromEnd];
    this.readMemory.get(b);
    debug(b);
    return b;
  }

  public AdbPollingCommand getListenerHolder() {
    return listenerHolder;
  }

  private void debug(byte[] b) {
    if(LOG.isLoggable(Level.FINER)) {
      LOG.finer("HEX:" + byteArrayToHex(b));
      LOG.finer("STR:" + new String(b));
    }
  }
  
  private String byteArrayToHex(byte[] a) {
    StringBuilder sb = new StringBuilder();
    for(final byte b: a)
      sb.append(String.format("%02x ", b&0xff));
    return sb.toString();
  }

  public void fillWriteBuffer(String message) {
    this.writeBuffer.put(message.getBytes());
    this.writeBuffer.flip();
  }
  
  public void fillWriteBufferForAdb(String message) {
    this.writeBuffer.put(AdbChannelMessage.makeHexLengthOfThePayload(message).getBytes());
    this.fillWriteBuffer( message);
  }
  
}
