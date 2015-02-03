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
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.FilenameFilter;


public class SlideShowActivity extends Activity implements Handler.Callback
{
   private static final String LOG_TAG = "SlideShowActivity";
   private Handler mUiHandler;
   private BtRcManager mBtRcManager;
   private SlideShowPresentation mSlideShowPresentation;
   private TextView mImageText;
   private TextView mIntervalText;
   private Switch mLoopSwitch;
   private SeekBar mIntervalSeek;
   private ProgressBar mImageProgressBar;
   private ToggleButton mStartStopBtn;
   private ToggleButton mPauseResumeBtn;
   private ToggleButton mShowTestBtn;
   private String mGalleryDirPath;
   private String[] mImageFileNames;
   private volatile boolean mIsBtEnabled;
   private volatile boolean mIsPlaying;
   private volatile boolean mLoop;
   private volatile boolean mDone;
   private int mNumberOfImages;
   private int mNextImageNumber;
   private int mIntervalSec = 3;
   private int mCurrentInterval;
   private int mMode;


   public static final int AUTOPLAY_SWITCH_VIEW = 0;


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
            uiUpdateMsg = new RcMessage();
            int nextImageNumber = mNextImageNumber == 0 ? mNumberOfImages : mNextImageNumber;
            String nextImage = mGalleryDirPath + "/" + mImageFileNames[mNextImageNumber];
            mSlideShowPresentation.switchView(nextImage);
            mImageProgressBar.setProgress(nextImageNumber);
            uiUpdateMsg.TYPE = RcMessage.UI_UPDATE;
            uiUpdateMsg.UI_STATE = new int[]{RcMessage.IMAGE_PROGRESS, nextImageNumber};
            mBtRcManager.sendMessage(uiUpdateMsg);
            mImageText.setText(String.format(getString(R.string.img_progress), nextImageNumber, mNumberOfImages));

            if (!mDone) {
               mUiHandler.sendEmptyMessageDelayed(AUTOPLAY_SWITCH_VIEW, mIntervalSec * 1000);
            } else {
               mPauseResumeBtn.performClick();
            }

            mDone = !mLoop && (mNextImageNumber == mNumberOfImages - 1);
            mNextImageNumber = (mNextImageNumber + 1) % mNumberOfImages;
            break;
         case BtRcManager.STATE_CHANGED:
            Log.d(LOG_TAG, "State changed to: " + msg.obj.toString());

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
               break;
            }
            break;
         default:
            return false;
      }

      return true;
   }


   public void startAutoPlay()
   {
      if (mIsPlaying) {
         return;
      }

      Log.d(LOG_TAG, "Start auto play");
      mUiHandler.removeCallbacksAndMessages(null);
      mSlideShowPresentation.resetPresentation(mGalleryDirPath + "/" + mImageFileNames[0],
              mGalleryDirPath + "/" + mImageFileNames[1]);
      //mFlipper.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
      Toast.makeText(this, "Auto play started! ", Toast.LENGTH_SHORT).show();
      mUiHandler.sendEmptyMessageDelayed(AUTOPLAY_SWITCH_VIEW, mIntervalSec * 2000);
      mPauseResumeBtn.setEnabled(true);
      mShowTestBtn.setChecked(false);
      mShowTestBtn.setEnabled(false);
      mDone = false;
      mNextImageNumber = 2;
      mIsPlaying = true;
      mImageProgressBar.setProgress(1);
      mImageText.setText(String.format(getString(R.string.img_progress), 1, mNumberOfImages));
      mImageText.setVisibility(View.VISIBLE);
      mImageProgressBar.setVisibility(View.VISIBLE);
   }

   public void resumeAutoPlay()
   {
      if (mIsPlaying) {
         return;
      }

      Toast.makeText(this, "Auto play resumed! ", Toast.LENGTH_SHORT).show();
      mUiHandler.sendEmptyMessage(AUTOPLAY_SWITCH_VIEW);
      mIsPlaying = true;
   }

   public void pauseAutoPlay()
   {
      if (!mIsPlaying) {
         return;
      }

      mUiHandler.removeCallbacksAndMessages(null);
      Toast.makeText(this, "Auto play paused! ", Toast.LENGTH_SHORT).show();
      mIsPlaying = false;
   }


   public void stopAutoPlay()
   {
      Log.d(LOG_TAG, "Stop auto play");
      mIsPlaying = false;
      mUiHandler.removeCallbacksAndMessages(null);
      mImageText.setVisibility(View.INVISIBLE);
      mImageProgressBar.setVisibility(View.INVISIBLE);
      mSlideShowPresentation.stopAutoPlay();
      mPauseResumeBtn.setChecked(false);
      mPauseResumeBtn.setEnabled(false);
      mShowTestBtn.setEnabled(true);
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

      mStartStopBtn = (ToggleButton) findViewById(R.id.start_stop_tgl);
      mStartStopBtn.setOnClickListener(mOnClickListener);

      mPauseResumeBtn = (ToggleButton) findViewById(R.id.pause_resume_tgl);
      mPauseResumeBtn.setOnClickListener(mOnClickListener);

      mShowTestBtn = (ToggleButton) findViewById(R.id.show_test_tgl);
      mShowTestBtn.setOnClickListener(mOnClickListener);

      mIntervalSeek = (SeekBar) findViewById(R.id.interval_seek);
      mIntervalSeek.setOnSeekBarChangeListener(mSeekBarListener);

      mIntervalSeek.setProgress(mIntervalSec);
      mSlideShowPresentation = new SlideShowPresentation(this, presentationDisplay, 0);

      if (startIntent.getDataString() != null) {
         File imgFile = new File(startIntent.getData().getPath());
         galleryDir = imgFile.getParent();
      } else {
         galleryDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
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
              mStartStopBtn.isEnabled() ? RcMessage.ENABLED : RcMessage.DISABLED,
              mStartStopBtn.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.PAUSE_BTN,
              mPauseResumeBtn.isEnabled() ? RcMessage.ENABLED : RcMessage.DISABLED,
              mPauseResumeBtn.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.TEST_BTN,
              mShowTestBtn.isEnabled() ? RcMessage.ENABLED : RcMessage.DISABLED,
              mShowTestBtn.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.LOOP_SWITCH,
              mLoopSwitch.isChecked() ? RcMessage.ON : RcMessage.OFF,
              RcMessage.INTERVAL_SEEK, mIntervalSeek.getProgress(),
              RcMessage.IMAGE_NUMBER, mImageProgressBar.getMax(),
              RcMessage.IMAGE_PROGRESS, mImageProgressBar.getProgress()
      };
   }


   private View.OnClickListener mOnClickListener = new View.OnClickListener()
   {
      @Override
      public void onClick(View v)
      {
         if (v instanceof CompoundButton) {
            RcMessage uiUpdateMsg = new RcMessage();
            uiUpdateMsg.TYPE = RcMessage.UI_UPDATE;
            CompoundButton btn = (CompoundButton) v;
            boolean isChecked = btn.isChecked();
            int btnId = btn.getId();

            switch (btnId) {
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
                  if (isChecked) {
                     mSlideShowPresentation.showTest();
                  } else {
                     mSlideShowPresentation.hide();
                  }
                  break;
               case R.id.loop_switch:
                  mLoop = isChecked;
                  break;
               default:
                  return;
            }

            if (mBtRcManager.getState() == BtRcManager.State.CONNECTED) {
               uiUpdateMsg.UI_STATE = getUiState();
               mBtRcManager.sendMessage(uiUpdateMsg);
            }
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
         mIntervalSec = seekBar.getProgress() + 2;
         mIntervalText.setText(mIntervalSec + " sec.");
      }
   };
}
