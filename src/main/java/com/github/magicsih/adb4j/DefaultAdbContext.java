/**
 * 
 */
package com.github.magicsih.adb4j;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import com.github.magicsih.adb4j.exception.AdbNotFoundException;

/**
 * @author sih
 *
 */
public class DefaultAdbContext implements AdbContext {

  private static final Logger LOG = Logger.getLogger(DefaultAdbContext.class.getName());

  private final ExecutorService adbWorker; // Worker for AsynchronousSocketChannel
  private final ExecutorService postWorker; // Worker for ADPC Device Listener
  private AsynchronousChannelGroup asyncChannelGroup;
  private SocketAddress addr;

  public DefaultAdbContext() {
    this(Executors.newFixedThreadPool(5), Executors.newFixedThreadPool(10));
  }

  public DefaultAdbContext(ExecutorService adbWorker, ExecutorService postWorker) {
    super();
    this.adbWorker = adbWorker;
    this.postWorker = postWorker;
  }

  @Override
  public void init() throws AdbNotFoundException {
    this.init(new AdbSocketAddress());
  }

  @Override
  public void init(AdbSocketAddress addr) throws AdbNotFoundException {
    try {
      this.asyncChannelGroup = AsynchronousChannelGroup.withThreadPool(this.adbWorker);
      try (AsynchronousSocketChannel ch = AsynchronousSocketChannel.open(asyncChannelGroup)) {
        ch.connect(addr).get();
      } catch (InterruptedException | ExecutionException e) {
        throw new AdbNotFoundException(e);
      }
    } catch (IOException e) {
      e.printStackTrace();
      this.asyncChannelGroup = null;
    }

    this.addr = addr;
  }

  @Override
  public List<String> getOnlineDeviceIds() {
    if (this.asyncChannelGroup == null) {
      throw new RuntimeException("call init method first");
    }

    ByteBuffer buf = ByteBuffer.allocate(512);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try (AsynchronousSocketChannel channel = AsynchronousSocketChannel.open(asyncChannelGroup)) {
      channel.connect(addr).get();
      channel.write(ByteBuffer.wrap(AdbChannelMessage.makeAdbPayloadBytes("host:devices-l"))).get();

      int read = 0;
      do {
        read = channel.read(buf).get();
        buf.flip();
        int limit = buf.limit();
        byte[] b = new byte[limit];
        buf.get(b, 0, limit);
        buf.clear();
        baos.write(b);
      } while (read != -1);

      byte[] result = baos.toByteArray();

      if (Arrays.equals(AdbChannelMessage.OKAY.getPayload(), Arrays.copyOfRange(result, 0, 4))) {
        final List<String> deviceIds = new ArrayList<>();

        String payload = new String(Arrays.copyOfRange(result, 8, result.length));
        String[] splitIntoLineBreak = payload.split("\n");


        for (String l : splitIntoLineBreak) {
          StringBuilder devicecIdBuilder = new StringBuilder();
          StringBuilder deviceStatusBuilder = new StringBuilder();

          char[] cs = l.toCharArray();

          for (int i = 0; i < cs.length; ++i) {
            if (cs[i] == ' ') {
              for (int j = i; j < cs.length; ++j) {
                if (cs[j] == ' ' && deviceStatusBuilder.length() == 0) {
                  continue;
                } else if (cs[j] == ' ' && deviceStatusBuilder.length() > 0) {
                  break;
                } else {
                  deviceStatusBuilder.append(cs[j]);
                }
              }
              break;
            }
            devicecIdBuilder.append(cs[i]);
          }
          String deviceId = devicecIdBuilder.toString();
          String status = deviceStatusBuilder.toString();
          LOG.fine("getOnlineDeviceIds - deviceId:" + deviceId + " status:" + status);
          if (status.equals("device")) {
            deviceIds.add(deviceId);
          }
        }
        return deviceIds;
      }

    } catch (IOException | InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }

    return Collections.emptyList();
  }

  @Override
  public void shutdown() {
    this.asyncChannelGroup.shutdown();
    this.adbWorker.shutdown();
    this.postWorker.shutdown();
  }


  @Override
  public AdbDevice getAdpcDeviceById(String deviceId) {
    assert (asyncChannelGroup != null);
    List<String> onlineDeviceIds = this.getOnlineDeviceIds();
    assert (onlineDeviceIds.contains(deviceId));

    Iterator<String> it = onlineDeviceIds.iterator();

    while (it.hasNext()) {
      String id = it.next();
      if (id.equals(deviceId)) {
        try {
          AdbDevice device =
              new AdbDevice(id, this.asyncChannelGroup, this.addr, this.postWorker);
          return device;
        } catch (IOException e) {
          e.printStackTrace();
          return null;
        }
      }
    }

    return null;
  }

  @Override
  public List<AdbDevice> getAdpcDevices() {
    assert (asyncChannelGroup != null);
    List<AdbDevice> apdcDevices = new ArrayList<>();
    List<String> onlineDeviceIds = this.getOnlineDeviceIds();
    Iterator<String> it = onlineDeviceIds.iterator();
    while (it.hasNext()) {
      String id = it.next();
      try {
        AdbDevice device = new AdbDevice(id, this.asyncChannelGroup, this.addr, this.postWorker);
        apdcDevices.add(device);
      } catch (IOException e) {
        e.printStackTrace();
        continue;
      }
    }

    return apdcDevices;
  }

}
