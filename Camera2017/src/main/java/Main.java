import java.util.ArrayList;

import edu.wpi.first.wpilibj.networktables.*;
import edu.wpi.first.wpilibj.tables.*;
import edu.wpi.cscore.*;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.HashMap;

import org.opencv.core.*;
import org.opencv.core.Core.*;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.*;
import org.opencv.objdetect.*;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

public class Main {
  public static void main(String[] args) {
    // Loads our OpenCV library. This MUST be included
    System.loadLibrary("opencv_java310");

    // Connect NetworkTables, and get access to the publishing table
	NetworkTable.setClientMode();
    // Set your team number here
    NetworkTable.setTeam(4662);
//	NetworkTable.setIPAddress("10.46.62.3");

    NetworkTable.initialize();
	NetworkTable visionTable = NetworkTable.getTable("Vision");
	
	final GpioController gpio = GpioFactory.getInstance();
	// provision gpio pin #01 as an output pin and turn on
    final GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "MyLED", PinState.HIGH);
    // set shutdown state for this pin
    pin.setShutdownOptions(true, PinState.LOW);

   
    // This is the network port you want to stream the raw received image to
    // By rules, this has to be between 1180 and 1190, so 1185 is a good choice
    int streamPort = 1185;
    int streamPort2 = 1187;

    // This stores our reference to our mjpeg server for streaming the input image
    MjpegServer inputStream = new MjpegServer("MJPEG Server", streamPort);
    MjpegServer inputStream2 = new MjpegServer("MJPEG Server2", streamPort2);


    /***********************************************/

    // USB Camera
    /*
    // This gets the image from a USB camera 
    // Usually this will be on device 0, but there are other overloads
    // that can be used
    UsbCamera camera = setUsbCamera(0, inputStream);
    // Set the resolution for our camera, since this is over USB
    camera.setResolution(640,480);
    */
    UsbCamera camera = setUsbCamera(0, inputStream);
    camera.setResolution(320,240);
    UsbCamera cam2 = setUsbCamera(1, inputStream2);
    cam2.setResolution(320, 240);

    // This creates a CvSink for us to use. This grabs images from our selected camera, 
    // and will allow us to use those images in opencv
    CvSink imageSink = new CvSink("CV Image Grabber");
    imageSink.setSource(camera);

    // This creates a CvSource to use. This will take in a Mat image that has had OpenCV operations
    // operations 
    CvSource imageSource = new CvSource("CV Image Source", VideoMode.PixelFormat.kMJPEG, 320, 240, 15);
    MjpegServer cvStream = new MjpegServer("CV Image Stream", 1186);
    cvStream.setSource(imageSource);

    // All Mats and Lists should be stored outside the loop to avoid allocations
    // as they are expensive to create
    Mat inputImage = new Mat();
    Mat hsv = new Mat();
	boolean bVisionInd = false;
	visionTable.putBoolean("Take Pic", bVisionInd);
	boolean bCameraLoop = true;
	visionTable.putBoolean("Keep Running", bCameraLoop);
    // Infinitely process image
//    while (true) {
    int i=0;
//    while (i < 10) {
	while (bCameraLoop) {
      // Grab a frame. If it has a frame time of 0, there was an error.
      // Just skip and continue
      long frameTime = imageSink.grabFrame(inputImage);
      if (frameTime == 0) continue;

      // Below is where you would do your OpenCV operations on the provided image
      // The sample below just changes color source to HSV
      Imgproc.cvtColor(inputImage, hsv, Imgproc.COLOR_BGR2HSV);

      // Here is where you would write a processed image that you want to restreams
      // This will most likely be a marked up image of what the camera sees
      // For now, we are just going to stream the HSV image
//      imageSource.putFrame(hsv);

		visionTable.putNumber("Next Pic", i);
		bVisionInd = visionTable.getBoolean("Take Pic", bVisionInd);
		if (bVisionInd) {
			char cI = Character.forDigit(i, 10);
//			Imgcodecs.imwrite("/home/pi/vid" + cI + ".jpg", inputImage);
			String fileTmst = new SimpleDateFormat("yyyyMMddhhmmssSSS").format(new Date().getTime());
			Imgcodecs.imwrite("/home/pi/vid" + fileTmst + ".jpg", inputImage);
			System.out.println("loop" + i);
			i++;
			bVisionInd = false;
			visionTable.putBoolean("Take Pic", bVisionInd);
			if (i > 9) {
				bCameraLoop = false;
			}
		}
		bCameraLoop = visionTable.getBoolean("Keep Running", bCameraLoop);
	}
	
    gpio.shutdown();

  }

  private static UsbCamera setUsbCamera(int cameraId, MjpegServer server) {
    // This gets the image from a USB camera 
    // Usually this will be on device 0, but there are other overloads
    // that can be used
    UsbCamera camera = new UsbCamera("CoprocessorCamera", cameraId);
    camera.setBrightness(30);
    camera.setExposureManual(10);
    camera.setWhiteBalanceManual(2800);
    camera.setFPS(15);
    server.setSource(camera);
    return camera;
  }
}