/**
 * 
 */
package com.github.magicsih.adb4j;

/**
 * @author sih
 *
 */
public enum AdbChannelMessage {

  OKAY("OKAY".getBytes());
  
  private final byte[] payload;
  
  private AdbChannelMessage(byte[] payload) {
    this.payload = payload;
  }
  
  public byte[] getPayload() {
    return payload;
  }
  
  /**
   * message 의 길이를 4자리 Hexadecimal로 보내준다.
   * 
   * @param message
   * @return
   */
  public static String makeHexLengthOfThePayload(String message) {
    return String.format("%4s", Integer.toHexString(message.length()).toUpperCase()).replaceAll(" ", "0");
  }

  public static byte[] makeAdbPayloadBytes(String message) {
    return (String.format("%4s", Integer.toHexString(message.length()).toUpperCase()).replaceAll(" ", "0") + message).getBytes();
  }
}
