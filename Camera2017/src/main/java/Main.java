import java.util.ArrayList;

import edu.wpi.first.wpilibj.networktables.*;
import edu.wpi.first.wpilibj.tables.*;
import edu.wpi.cscore.*;

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
		NetworkTable.setTeam(4662);
		NetworkTable.initialize();
		NetworkTable visionTable = NetworkTable.getTable("Vision");
	
	//gpio for relay light control
		final GpioController gpio = GpioFactory.getInstance();
		final GpioPinDigitalOutput gearLight = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "Gear", PinState.LOW);
		gearLight.setShutdownOptions(true, PinState.LOW);
		final GpioPinDigitalOutput fuelLight = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01, "Fuel", PinState.LOW);
		fuelLight.setShutdownOptions(true, PinState.LOW);

   
    // This is the network port you want to stream the raw received image to
    // By rules, this has to be between 1180 and 1190, so 1185 is a good choice
//    	int streamPort = 1185;
//    	int streamPort2 = 1187;
    // This stores our reference to our mjpeg server for streaming the input image
 //   	MjpegServer inputStream = new MjpegServer("MJPEG Server", streamPort);
 //   	MjpegServer inputStream2 = new MjpegServer("MJPEG Server2", streamPort2);
//    	UsbCamera camera = setUsbCamera("GearCam",0, inputStream);
//    	UsbCamera cam2 = setUsbCamera("ShootCam",1, inputStream2);
		UsbCamera camera = setUsbCamera("GearCam",0);
		UsbCamera cam2 = setUsbCamera("ShootCam",1);


    // This creates a CvSink for us to use. This grabs images from our selected camera, 
    // and will allow us to use those images in opencv
		CvSink imageSink = new CvSink("CV Image Grabber");
		imageSink.setSource(camera);

    // This creates a CvSource to use. This will take in a Mat image that has had OpenCV operations
    // operations 
		CvSource imageSource = new CvSource("CV Image Source", VideoMode.PixelFormat.kMJPEG, 640, 480, 15);
		MjpegServer cvStream = new MjpegServer("CV Image Stream", 1186);
		cvStream.setSource(imageSource);

    // All Mats and Lists should be stored outside the loop to avoid allocations
    // as they are expensive to create
		Mat inputImage = new Mat();
//    Mat hsv = new Mat();
		PegFilter gearTarget = new PegFilter();
    
		boolean bVisionInd = false;
		boolean bCameraLoop = true;
		boolean bGearDrive = true;
		boolean bVisionOn = false;
		do {
			visionTable.putBoolean("Take Pic", bVisionInd);
			visionTable.putBoolean("Keep Running", bCameraLoop);
			visionTable.putBoolean("IsGearDrive", bGearDrive);
			visionTable.putBoolean("IsVisionOn", bVisionOn);
		} while (!visionTable.isConnected());
	
		double r1PixelX = -1;
		double r1PixelY = -1;
		double r1PixelH = -1;
		double r1PixelW = -1;
		boolean bIsTargetFound = false;

		// Infinitely process image
	    int i=0;
		while (bCameraLoop) {
	
			bGearDrive = visionTable.getBoolean("IsGearDrive", bGearDrive);
			bVisionOn = visionTable.getBoolean("IsVisionOn", bVisionOn);
	
	      	if (bVisionOn) {
	      		if (bGearDrive) {
	      			gearLight.high();
	      			fuelLight.low();
	      		} else {
	      			fuelLight.high();
	      			gearLight.low();
	      		}
	      	} else {
	      		gearLight.low();
	      		fuelLight.low();
	      	}
	  		if (bGearDrive) {
	  		    imageSink.setSource(camera);
	  		} else {
	  		    imageSink.setSource(cam2);
	  		}
	      	
	      // Grab a frame. If it has a frame time of 0, there was an error.
	      // Just skip and continue
	  		long frameTime = imageSink.grabFrame(inputImage);
	  		if (frameTime == 0) continue;
	
	      // Below is where you would do your OpenCV operations on the provided image
	      // The sample below just changes color source to HSV
	//      Imgproc.cvtColor(inputImage, hsv, Imgproc.COLOR_BGR2HSV);
			bIsTargetFound = false;
			int objectsFound = 0;
			if (bGearDrive) {
				gearTarget.process(inputImage);
			 	objectsFound = gearTarget.filterContoursOutput().size();
			} else {
				objectsFound = 0;
			}
	    
		   	if (objectsFound == 1) {
		   		bIsTargetFound = true;
		    	Rect r1 = Imgproc.boundingRect(gearTarget.filterContoursOutput().get(0));
		    	r1PixelX = r1.x;
		    	r1PixelY = r1.y;
		    	r1PixelW = r1.width;
		    	r1PixelH = r1.height;
		    	
		   	} else {
		    	r1PixelX = -1;
		    	r1PixelY = -1;
		    	r1PixelW = -1;
		    	r1PixelH = -1;
		   	}
		   	
		   	if (bGearDrive) {
		   		Imgproc.rectangle(inputImage, new Point(240,240), new Point(400,320), new Scalar(255, 255,255), 5);
		   	} else {
		   		Imgproc.rectangle(inputImage, new Point(240,10), new Point(400,120), new Scalar(255, 255,255), 5);
		   	}
	
		   	visionTable.putBoolean("IsTargetFound", bIsTargetFound);
		    visionTable.putNumber("TargetX", r1PixelX);
		    visionTable.putNumber("TargetY", r1PixelY);
		    visionTable.putNumber("TargetWidth", r1PixelW);
		    visionTable.putNumber("TargetHeight", r1PixelH);
	
	    	visionTable.putNumber("Next Pic", i);
	    	bVisionInd = visionTable.getBoolean("Take Pic", bVisionInd);
	    	if (bVisionInd) {
	    		char cI = Character.forDigit(i, 10);
	    		String fileTmst = new SimpleDateFormat("yyyyMMddhhmmssSSS").format(new Date().getTime());
	    		Imgcodecs.imwrite("/home/pi/vid" + fileTmst + ".jpg", inputImage);
	    		System.out.println("loop" + i);
	    		i++;
	    		bVisionInd = false;
	    		visionTable.putBoolean("Take Pic", bVisionInd);
	    	}
	    	bCameraLoop = visionTable.getBoolean("Keep Running", bCameraLoop);
	    	imageSource.putFrame(inputImage);
	    }
	
    gpio.shutdown();

	}

//  UsbCamera v1 - no mjpeg streamer
  private static UsbCamera setUsbCamera(String camName, int cameraId) {
    // This gets the image from a USB camera 
    // Usually this will be on device 0, but there are other overloads
    // that can be used
    UsbCamera camera = new UsbCamera(camName, cameraId);
    camera.setResolution(640,480);
    camera.setBrightness(40);
    camera.setExposureManual(10);
    camera.setWhiteBalanceManual(2800);
    camera.setFPS(20);
    System.out.println(camera.getName());
    System.out.println(camera.getDescription());
    System.out.println(camera.getPath());
    System.out.println(camera.getKind());
    System.out.println(camera.isConnected());
    return camera;
  }
  
  // UsbCamera v2 with mjpeg for testing/monitoring - not recommended for game use
  	private static UsbCamera setUsbCamera(String camName, int cameraId, MjpegServer server) {
	    // This gets the image from a USB camera 
	    // Usually this will be on device 0, but there are other overloads
	    // that can be used
	    UsbCamera camera = new UsbCamera(camName, cameraId);
	    camera.setResolution(640,480);
	    camera.setBrightness(40);
	    camera.setExposureManual(10);
	    camera.setWhiteBalanceManual(2800);
	    camera.setFPS(15);
	    server.setSource(camera);
	    return camera;
	}
  	
}