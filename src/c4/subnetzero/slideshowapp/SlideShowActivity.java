package c4.subnetzero.slideshowapp;

import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;


public class SlideShowActivity extends Activity implements Handler.Callback
{
   private static final String LOG_TAG = "SlideShowActivity";
   private Handler mUiHandler;
   private BtRcManager mBtRcManager;
   private SlideShowPresentation mSlideShowPresentation;
   private TextView mImageText;
   private TextView mIntervalText;
   private Switch mLoopSwitch;
   private Switch mAutoSwitch;
   private SeekBar mIntervalSeek;
   private ProgressBar mImageProgressBar;
   private ToggleButton mStartStopBtn;
   private ToggleButton mPauseResumeBtn;
   private ToggleButton mShowTestBtn;
   private Button mPreviousImageBtn;
   private Button mNextImageBtn;
   private String mGalleryDirPath;
   private String[] mImageFileNames;
   //private volatile boolean mIsBtEnabled;
   private volatile boolean mIsPlaying;
   private volatile boolean mLoop;
   private volatile boolean mDone;
   private volatile boolean mBusy;
   private volatile boolean mAutoPlay;
   private int mNumberOfImages;
   private int mNextImageIndex;
   private int mInterval = 3;


   public static final int AUTOPLAY_SWITCH_VIEW = 0;
   public static final int TRANSISION_FINISHED = 1;


   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.slideshow_activity);
      mUiHandler = new Handler(this);
      setup();
   }


   @Override
   protected void onResume()
   {
      Log.d(LOG_TAG, "onResume()");
      super.onResume();

      if (mSlideShowPresentation != null) {
         mSlideShowPresentation.show();
         reset();
         mSlideShowPresentation.showTest(true);
         mShowTestBtn.setChecked(true);
      }

      if (mBtRcManager != null && mBtRcManager.isBtEnabled()) {
         mBtRcManager.startAcceptThread();
      }
   }

   @Override
   protected void onPause()
   {
      Log.d(LOG_TAG, "onPause()");
      super.onPause();

      if (mIsPlaying) {
         stopAutoPlay();
      }

      if (mSlideShowPresentation != null) {
         mSlideShowPresentation.dismiss();
      }

      if (mBtRcManager != null) {
         mBtRcManager.close();
      }
   }

   @Override
   protected void onDestroy()
   {
      Log.d(LOG_TAG, "onDestroy()");
      super.onDestroy();

   }

   @Override
   protected void onRestoreInstanceState(Bundle savedInstanceState)
   {
      Log.d(LOG_TAG, "onRestoreInstanceState()");
      super.onRestoreInstanceState(savedInstanceState);
   }

   @Override
   protected void onSaveInstanceState(Bundle outState)
   {
      Log.d(LOG_TAG, "onSaveInstanceState()");
      super.onSaveInstanceState(outState);
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data)
   {
      /*
      if (requestCode == BtRcManager.REQUEST_DISCOVERABLE_BT && resultCode != RESULT_CANCELED) {
         Log.d(LOG_TAG, "BT discover result code: " + resultCode);
         Toast.makeText(this, "BT Discover enabled", Toast.LENGTH_SHORT);
         mIsBtEnabled = true;
         mBtRcManager.startAcceptThread();
      }*/

      if (requestCode == BtRcManager.REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
         mBtRcManager.startAcceptThread();
      }
   }


   @Override
   public boolean handleMessage(Message msg)
   {
      RcMessage uiUpdateMsg;

      switch (msg.what) {
         case AUTOPLAY_SWITCH_VIEW:
            incrementImageIndex();
            if (mLoop || (mNextImageIndex + 1 < mNumberOfImages)) {
               mUiHandler.sendEmptyMessageDelayed(AUTOPLAY_SWITCH_VIEW, getInterval(mImageFileNames[mNextImageIndex]));
            } else {
               mPauseResumeBtn.performClick();
            }
            showNextImage();
            break;
         case TRANSISION_FINISHED:
            mBusy = false;
            break;
         case BtRcManager.STATE_CHANGED:

            if (mBtRcManager.getState() == BtRcManager.State.CONNECTED) {
               uiUpdateMsg = new RcMessage();
               uiUpdateMsg.TYPE = RcMessage.UI_UPDATE;
               uiUpdateMsg.UI_STATE = getUiState();
               mBtRcManager.sendMessage(uiUpdateMsg);
            }

            break;
         case BtRcManager.MESSAGE_RECEIVED:
            RcMessage rcMsg = (RcMessage) msg.obj;
            if (rcMsg.TYPE == RcMessage.COMMAND) {
               execCommand(rcMsg);
            }
            break;
         default:
            return false;
      }

      return true;
   }


   public void reset()
   {
      mUiHandler.removeCallbacksAndMessages(null);
      mImageProgressBar.setProgress(1);
      mImageText.setText(String.format(getString(R.string.img_progress), 1, mNumberOfImages));
      mSlideShowPresentation.resetPresentation(mGalleryDirPath + "/" + mImageFileNames[0]);
      mShowTestBtn.setEnabled(true);
      mNextImageIndex = 0;
   }


   public void startAutoPlay()
   {
      if (mIsPlaying) {
         return;
      }

      Log.d(LOG_TAG, "Start auto play");
      mAutoPlay = true;
      reset();
      mUiHandler.sendEmptyMessageDelayed(AUTOPLAY_SWITCH_VIEW, getInterval(mImageFileNames[0]));
      mPauseResumeBtn.setEnabled(true);
      mShowTestBtn.setChecked(false);
      mShowTestBtn.setEnabled(false);
      mDone = false;
      mIsPlaying = true;
   }

   public void resumeAutoPlay()
   {
      if (mIsPlaying) {
         return;
      }

      mUiHandler.sendEmptyMessage(AUTOPLAY_SWITCH_VIEW);
      mIsPlaying = true;
   }

   public void pauseAutoPlay()
   {
      if (!mIsPlaying) {
         return;
      }

      mUiHandler.removeCallbacksAndMessages(null);
      mIsPlaying = false;
   }


   public void stopAutoPlay()
   {
      Log.d(LOG_TAG, "Stop auto play");
      mIsPlaying = false;
      mAutoPlay = false;
      mUiHandler.removeCallbacksAndMessages(null);
      //mImageText.setVisibility(View.INVISIBLE);
      //mImageProgressBar.setVisibility(View.INVISIBLE);
      mSlideShowPresentation.stopAutoPlay();
      mPauseResumeBtn.setChecked(false);
      mPauseResumeBtn.setEnabled(false);
      mShowTestBtn.setEnabled(true);
   }

   public void showNextImage()
   {
      if (mBusy) {
         return;
      }

      RcMessage uiUpdateMsg;
      mBusy = true;
      mSlideShowPresentation.switchView(mGalleryDirPath + "/" + mImageFileNames[mNextImageIndex]);
      mImageProgressBar.setProgress(mNextImageIndex + 1);
      uiUpdateMsg = new RcMessage();
      uiUpdateMsg.TYPE = RcMessage.UI_UPDATE;
      uiUpdateMsg.UI_STATE = new int[]{RcMessage.IMAGE_PROGRESS, mNextImageIndex + 1, View.VISIBLE};
      mBtRcManager.sendMessage(uiUpdateMsg);
      mImageText.setText(String.format(getString(R.string.img_progress), mNextImageIndex + 1, mNumberOfImages));
   }

   private void setup()
   {
      Display presentationDisplay;
      String galleryDir;
      String[] imageFileNames;
      Intent startIntent = getIntent();

      //Keep screen ON while in use
      getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

      DisplayManager dpManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
      Display[] displays = dpManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);

      if (displays.length < 1) {
         presentationDisplay = getWindowManager().getDefaultDisplay();
      } else {
         presentationDisplay = displays[0];
      }

      mImageProgressBar = (ProgressBar) findViewById(R.id.img_progress_bar);
      mIntervalText = (TextView) findViewById(R.id.interval_value);

      mImageText = (TextView) findViewById(R.id.img_progress_label);
      mLoopSwitch = (Switch) findViewById(R.id.loop_switch);
      mLoopSwitch.setOnClickListener(mOnClickListener);

      mAutoSwitch = (Switch) findViewById(R.id.auto_switch);
      mAutoSwitch.setOnClickListener(mOnClickListener);

      mStartStopBtn = (ToggleButton) findViewById(R.id.start_stop_tgl);
      mStartStopBtn.setOnClickListener(mOnClickListener);

      mPauseResumeBtn = (ToggleButton) findViewById(R.id.pause_resume_tgl);
      mPauseResumeBtn.setOnClickListener(mOnClickListener);

      mShowTestBtn = (ToggleButton) findViewById(R.id.show_test_tgl);
      mShowTestBtn.setOnClickListener(mOnClickListener);

      mPreviousImageBtn = (Button) findViewById(R.id.previous_image_btn);
      mPreviousImageBtn.setOnClickListener(mOnClickListener);

      mNextImageBtn = (Button) findViewById(R.id.next_image_btn);
      mNextImageBtn.setOnClickListener(mOnClickListener);

      mIntervalSeek = (SeekBar) findViewById(R.id.interval_seek);
      mIntervalSeek.setOnSeekBarChangeListener(mSeekBarListener);

      mIntervalSeek.setProgress(mInterval);
      mSlideShowPresentation = new SlideShowPresentation(this, presentationDisplay, mUiHandler);

      if (startIntent.getDataString() != null) {
         File imgFile = new File(startIntent.getData().getPath());
         galleryDir = imgFile.getParent();
      } else {
         galleryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
         galleryDir += "/BDayTest";
      }

      imageFileNames = getImageFileNames(galleryDir);

      if (imageFileNames == null) {
         finish();
         return;
      }

      for (String fileName : imageFileNames) {
         Log.d(LOG_TAG, "Image: " + fileName);
      }

      setGallery(galleryDir, imageFileNames);
      mBtRcManager = new BtRcManager(this, mUiHandler, null);
   }


   public void setGallery(final String galleryDir, final String[] imageNames)
   {
      mGalleryDirPath = galleryDir;
      mImageFileNames = imageNames;
      mNumberOfImages = mImageFileNames.length;
      mImageProgressBar.setMax(mNumberOfImages);
   }

   private String[] getImageFileNames(final String galleryDir)
   {
      String[] imageNames;
      File picturesDir = new File(galleryDir);

      imageNames = picturesDir.list(new FilenameFilter()
      {
         @Override
         public boolean accept(File dir, String filename)
         {
            return filename.toLowerCase().endsWith(".jpg");
         }
      });

      Arrays.sort(imageNames);

      return imageNames;
   }

   private void execCommand(final RcMessage commandMsg)
   {
      switch (commandMsg.ELEMENT) {
         case RcMessage.START_BTN:
            mStartStopBtn.performClick();
            break;
         case RcMessage.PAUSE_BTN:
            mPauseResumeBtn.performClick();
            break;
         case RcMessage.TEST_BTN:
            mShowTestBtn.performClick();
            break;
         case RcMessage.LOOP_SWITCH:
            mLoopSwitch.performClick();
            break;
         case RcMessage.INTERVAL_SEEK:
            mIntervalSeek.setProgress(commandMsg.ARG1);
            mSeekBarListener.onStopTrackingTouch(mIntervalSeek);
         default:
            return;
      }
   }

   private int[] getUiState()
   {
      return new int[]{
              RcMessage.START_BTN,
              mStartStopBtn.isEnabled() ? RcMessage.ON : RcMessage.OFF,
              mStartStopBtn.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.PAUSE_BTN,
              mPauseResumeBtn.isEnabled() ? RcMessage.ON : RcMessage.OFF,
              mPauseResumeBtn.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.TEST_BTN,
              mShowTestBtn.isEnabled() ? RcMessage.ON : RcMessage.OFF,
              mShowTestBtn.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.LOOP_SWITCH,
              mLoopSwitch.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.INTERVAL_SEEK, mIntervalSeek.getProgress(),
              RcMessage.IMAGE_NUMBER, mImageProgressBar.getMax(),
              RcMessage.IMAGE_PROGRESS, mImageProgressBar.getProgress(),
              mImageProgressBar.getVisibility()
      };
   }

   private int getInterval(final String fileName)
   {
      int f = 1;
      // 134%12.jpg
      if (fileName.contains("%")) {
         f = 2;
         int i1 = fileName.lastIndexOf('%');
         int i2 = fileName.lastIndexOf('.');
         try {
            f = Integer.parseInt(fileName.substring(i1 + 1, i2));
         } catch (Exception e) {
            /* IGNORED */
         }
      }

      return 1000 * f * mInterval;
   }


   private synchronized void incrementImageIndex()
   {
      mNextImageIndex = (mNextImageIndex + 1) % mNumberOfImages;
   }

   private synchronized void decrementImageIndex()
   {
      mNextImageIndex = (mNextImageIndex == 0) ? mNumberOfImages - 1 : mNextImageIndex - 1;
   }

   private View.OnClickListener mOnClickListener = new View.OnClickListener()
   {
      @Override
      public void onClick(View view)
      {
         boolean isChecked = false;

         if (view instanceof CompoundButton) {
            isChecked = ((CompoundButton) view).isChecked();
         }

         switch (view.getId()) {
            case R.id.start_stop_tgl:
               if (isChecked) {
                  startAutoPlay();
               } else {
                  stopAutoPlay();
               }
               break;
            case R.id.pause_resume_tgl:
               if (isChecked) {
                  pauseAutoPlay();
               } else {
                  resumeAutoPlay();
               }
               break;
            case R.id.show_test_tgl:
               mSlideShowPresentation.showTest(isChecked);
               break;
            case R.id.previous_image_btn:
               decrementImageIndex();
               showNextImage();
               break;
            case R.id.next_image_btn:
               incrementImageIndex();
               showNextImage();
               break;
            case R.id.loop_switch:
               mLoop = isChecked;
               break;
            case R.id.auto_switch:
               mAutoPlay = isChecked;
               mPreviousImageBtn.setEnabled(!mAutoPlay);
               mNextImageBtn.setEnabled(!mAutoPlay);
            default:
               return;
         }

         if (mBtRcManager.getState() == BtRcManager.State.CONNECTED) {
            RcMessage uiUpdateMsg = new RcMessage();
            uiUpdateMsg.TYPE = RcMessage.UI_UPDATE;
            uiUpdateMsg.UI_STATE = getUiState();
            mBtRcManager.sendMessage(uiUpdateMsg);
         }
      }
   };


   private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener()
   {
      @Override
      public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
      {
         mIntervalText.setText((fromUser ? (2 + progress) : progress) + " sec.");
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar)
      {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar)
      {
         mInterval = seekBar.getProgress() + 2;
         mIntervalText.setText(mInterval + " sec.");
      }
   };
}
