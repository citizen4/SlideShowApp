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
   private static final int AUTOPLAY_SWITCH_VIEW = 0;
   private Handler mUiHandler;
   private SlideShowPresentation mSlideShowPresentation;
   private TextView mImageText;
   private TextView mIntervalText;
   private Switch mLoopSwitch;
   private SeekBar mIntervalSeek;
   private ToggleButton mStartStopBtn;
   private ToggleButton mPauseResumeBtn;
   private ToggleButton mShowTestBtn;
   private String mGalleryDirPath;
   private String[] mImageFileNames;
   private volatile boolean mIsPlaying;
   private volatile boolean mLoop;
   private volatile boolean mDone;
   private int mNumberOfImages;
   private int mNextImageNumber;
   private int mIntervalSec = 3;
   private int mCurrentInterval;
   private int mMode;


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
   }

   @Override
   protected void onPause()
   {
      Log.d(LOG_TAG, "onPause()");
      super.onPause();

      if(mIsPlaying) {
         stopAutoPlay();
      }
      if (mSlideShowPresentation != null) {
         mSlideShowPresentation.dismiss();
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
   public boolean handleMessage(Message msg)
   {
      switch (msg.what) {
         case AUTOPLAY_SWITCH_VIEW:
            String nextImage = mGalleryDirPath + "/" + mImageFileNames[mNextImageNumber];
            mSlideShowPresentation.switchView(nextImage);
            mImageText.setText(String.format("%d/%d",
                  mNextImageNumber == 0 ? mNumberOfImages : mNextImageNumber,mNumberOfImages));
            if (!mDone) {
               mUiHandler.sendEmptyMessageDelayed(AUTOPLAY_SWITCH_VIEW, mIntervalSec * 1000);
            } else {
               mPauseResumeBtn.setEnabled(false);
            }
            mDone = !mLoop && (mNextImageNumber == mNumberOfImages - 1);
            mNextImageNumber = (mNextImageNumber + 1) % mNumberOfImages;
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
      mImageText.setText(String.format("%d/%d",1,mNumberOfImages));
      mImageText.setVisibility(View.VISIBLE);
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

      mIntervalText = (TextView)findViewById(R.id.interval_value);

      mImageText = (TextView)findViewById(R.id.image_text);
      mLoopSwitch = (Switch)findViewById(R.id.loop_switch);
      mLoopSwitch.setOnClickListener(mOnClickListener);

      mStartStopBtn = (ToggleButton) findViewById(R.id.start_stop_tgl);
      mStartStopBtn.setOnClickListener(mOnClickListener);

      mPauseResumeBtn = (ToggleButton) findViewById(R.id.pause_resume_tgl);
      mPauseResumeBtn.setOnClickListener(mOnClickListener);

      mShowTestBtn = (ToggleButton) findViewById(R.id.show_test_tgl);
      mShowTestBtn.setOnClickListener(mOnClickListener);

      mIntervalSeek = (SeekBar)findViewById(R.id.interval_seek);
      mIntervalSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
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
            mIntervalSec = seekBar.getProgress()+2;
            mIntervalText.setText(mIntervalSec + " sec.");
         }
      });

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
   }


   public void setGallery(final String galleryDir, final String[] imageNames)
   {
      Log.d(LOG_TAG, "setGallery()");

      mGalleryDirPath = galleryDir;
      mImageFileNames = imageNames;
      mNumberOfImages = mImageFileNames.length;
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

   private View.OnClickListener mOnClickListener = new View.OnClickListener()
   {
      @Override
      public void onClick(View v)
      {
         if (v instanceof CompoundButton) {
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
            }
         }
      }
   };

}
