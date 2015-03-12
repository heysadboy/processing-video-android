package in.omerjerk.processing.video.android;

import java.util.ArrayList;
import java.util.List;

import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import processing.core.PConstants;
import processing.core.PApplet;
import processing.core.PImage;
import processing.opengl.PGraphicsOpenGL;

@SuppressWarnings("deprecation")
public class Capture extends PImage implements PConstants,
		SurfaceHolder.Callback, CameraHandlerCallback, SurfaceTexture.OnFrameAvailableListener {

	private static final boolean DEBUG = true;

	public static void log(String log) {
		if (DEBUG)
			System.out.println(log);
	}

	private PApplet applet;

	private Camera mCamera;

	private static ArrayList<String> camerasList = new ArrayList<String>();

	private static final String KEY_FRONT_CAMERA = "front-camera-%d";
	private static final String KEY_BACK_CAMERA = "back-camera-%d";

	private int selectedCamera = -1;

	private GLSurfaceView glView;
	private SurfaceTexture mSurfaceTexture;
	private FullFrameRect mFullScreen;
	private int mTextureId;

	private CameraHandler mCameraHandler;

	public Capture(PApplet context) {
		this(context, -1, -1);
	}

	public Capture(final PApplet applet, int width, int height) {
		this.applet = applet;
		if (width == -1 || height == -1) {
			//TODO: Temp hack. Needs to be handled intelligently.
			this.width = 1080;
			this.height = 1920;
		} else {
			this.width = width;
			this.height = height;
		}
		applet.registerMethod("pause", this);
		applet.registerMethod("resume", this);
		glView = (GLSurfaceView) applet.getSurfaceView();
		applet.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCameraHandler = new CameraHandler(Capture.this);
			}
		});

		glView.queueEvent(new Runnable() {
			@Override
			public void run() {
				mFullScreen = new FullFrameRect(
		                new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
				mTextureId = mFullScreen.createTextureObject();

				mSurfaceTexture = new SurfaceTexture(mTextureId);
				mSurfaceTexture.setOnFrameAvailableListener(Capture.this);
				mCameraHandler.sendMessage(mCameraHandler.obtainMessage(CameraHandler.MSG_START_CAMERA, null));
				System.out.println("sent starting message to UI thread");
			}
		});
	}

	public void setCamera(String camera) {
		if (camera == null || camera.equals("")) {
			selectedCamera = 0;
		} else {
			selectedCamera = camerasList.indexOf(camera);
		}
		log("Selected camera = " + selectedCamera);
		startCameraImpl(selectedCamera);
	}
	
	public void startCameraImpl(int cameraId) {
		try {
			mCamera = Camera.open(cameraId);
			mCamera.setDisplayOrientation(90);
			startPreview(applet.getSurfaceHolder());
		} catch (Exception e) {
			log("Couldn't open the Camera");
			e.printStackTrace();
		}
	}
	
	public void pause() {
		log("pause called");
		if (mCamera != null) {
			mCamera.release();
        }
		if (mFullScreen != null) {
            mFullScreen.release(false);     // assume the GLSurfaceView EGL context is about
            mFullScreen = null;             //  to be destroyed
        }
	}
	
	public void resume() {
		log("resume called");
		if (selectedCamera != -1) {
			startCameraImpl(selectedCamera);
		}
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		startPreview(holder);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		startPreview(holder);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// TODO: Release Camera resources
	}

	public String[] list() {
		if (applet.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA)) {
			int nOfCameras = Camera.getNumberOfCameras();
			for (int i = 0; i < nOfCameras; ++i) {
				Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
				Camera.getCameraInfo(i, cameraInfo);
				if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
					camerasList.add(String.format(KEY_FRONT_CAMERA, i));
				} else {
					// Back Camera
					camerasList.add(String.format(KEY_BACK_CAMERA, i));
				}
			}
			String[] array = new String[nOfCameras];
			camerasList.toArray(array);
			return array;
		}
		return null;
	}

	private void startPreview(SurfaceHolder mHolder) {

		if (mHolder.getSurface() == null) {
			// preview surface does not exist
			return;
		}

		// stop preview before making changes
		try {
			mCamera.stopPreview();
		} catch (Exception e) {
			// ignore: tried to stop a non-existent preview
		}

		// set preview size and make any resize, rotate or
		// reformatting changes here

		// start preview with new settings
		try {
			mCamera.setPreviewTexture(mSurfaceTexture);
			mCamera.startPreview();
			log("Started the preview");
		} catch (Exception e) {
			Log.d("PROCESSING",
					"Error starting camera preview: " + e.getMessage());
			e.printStackTrace();
		}
	}	

	static class CameraHandler extends Handler {
        public static final int MSG_SET_SURFACE_TEXTURE = 0;
        public static final int MSG_START_CAMERA = 1;

        // Weak reference to the Activity; only access this from the UI thread.
        private CameraHandlerCallback callback;

        public CameraHandler(CameraHandlerCallback c) {
        	callback = c;
        }

        @Override  // runs on UI thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            log("CameraHandler [" + this + "]: what=" + what);

//            MainActivity activity = mWeakActivity.get();
            if (callback == null) {
                return;
            }

            switch (what) {
                case MSG_SET_SURFACE_TEXTURE:
                	callback.handleSetSurfaceTexture((SurfaceTexture) inputMessage.obj);
                    break;
                case MSG_START_CAMERA:
                	callback.startCamera();
                	break;
                default:
                    throw new RuntimeException("unknown msg " + what);
            }
        }
    }

	public Camera getCamera() {
		return mCamera;
	}

	public static void printCompatibleResolutionsList(Capture capture) {
		Camera camera = capture.getCamera();
		List<Camera.Size> sizes = camera.getParameters()
				.getSupportedPreviewSizes();
		for (Size size : sizes) {
			System.out.println(size.width + "x" + size.height);
		}
	}

	@Override
	public void handleSetSurfaceTexture(SurfaceTexture st) {}
	
	@Override
	public void startCamera() {
		System.out.println("Start Camera Impl");
		startCameraImpl(0);
	};

	@Override
	public void onFrameAvailable(final SurfaceTexture surfaceTexture) {
		glView.queueEvent(new Runnable() {
			@Override
			public void run() {
				System.out.println("onFrameAvailable");
				surfaceTexture.updateTexImage();
				//TODO: Copy this texture to applet's texture
			}
		});
	}
}
