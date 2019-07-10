package com.github.magicsih.adb4j.exception;

public class AdbNotFoundException extends Exception {

  private static final String MESSAGE = "fail to connect to ADB. check if adb process is running and listening tcp 5037 port.";
  
  private static final long serialVersionUID = -1507956970997197057L;

  public AdbNotFoundException(Throwable cause) {
    super(MESSAGE, cause);
  }

  public AdbNotFoundException() {
    super(MESSAGE);
  }

  
}
