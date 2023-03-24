# ADB4J: Android Debug Bridge for Java

ADB4J is a Java library that provides an easy-to-use interface for Android Debug Bridge (ADB) commands within Java applications. With ADB4J, you can register device callbacks, and execute ADB commands in a Java programming model. The library is written in pure Java and requires no additional dependencies.

## Features
- Perform ADB commands using Java code
- Register device callbacks
- Pure Java implementation with no external dependencies
- One device per thread model

## Quick Start

### Maven Dependency

Add the following Maven dependency to your project:

```xml
<dependency>
    <groupId>com.github.magicsih</groupId>
    <artifactId>adb4j</artifactId>
    <version>0.0.1-SNAPSHOT</version>      
</dependency>
```

## Example Usage

Here is a simple example demonstrating how to use ADB4J:

```java
AdbContext adbContext = new DefaultAdbContext(
    Executors.newFixedThreadPool(10, new CustomizableThreadFactory("ADBNetwork-")),
    Executors.newFixedThreadPool(10, new CustomizableThreadFactory("AdpcCallback-"))
);
adbContext.init();

String packageName = "com.sec.android.app.clockpackage";

AdbDevice s8 = adbContext.getAdpcDeviceById("{your-android-device-id}");
s8.registerPollingListener(STAT_INFO_TOP, "top -p `pidof " + packageName + "` -o ARGS,%CPU -n 1\n", this, false);
s8.registerPollingListener("meminfo", "dumpsys meminfo " + packageName + "\n", this, false);
s8.start();

Thread.currentThread().join();   
adbContext.shutdown();
```
