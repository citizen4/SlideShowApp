package c4.subnetzero.slideshowapp;


import android.app.Presentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.ViewFlipper;

public class SlideShowPresentation extends Presentation
{
   private static final String LOG_TAG = "SlideShowPresentation";
   private static final int MAX_IMAGE_SIZE = 1600;
   private static final int NUM_OF_ANIMATIONS = 6;
   private Handler mUiHandler;
   private ViewFlipper mFlipper;
   private ImageView mImageViewA;
   private ImageView mImageViewB;
   //private Bitmap mImageBitmapA;
   //private Bitmap mImageBitmapB;
   private int[][] mAnimations;
   private String mNextImageFile;
   //private volatile boolean mFirstImage;
   private int mCounter;
   private float mWidthScaleFactor = 1.0f;

   public SlideShowPresentation(Context outerContext, Display display, int theme)
   {
      super(outerContext, display, theme);
   }

   @Override
   public void onCreate(Bundle savedInstanceState)
   {
      Log.d(LOG_TAG, "onCreate()");
      super.onCreate(savedInstanceState);
      setContentView(R.layout.slideshow_presentation);
      setup();
   }

   @Override
   public void onStart()
   {
      Log.d(LOG_TAG, "onStart()");
      super.onStart();
   }

   @Override
   public void onStop()
   {
      Log.d(LOG_TAG, "onStop()");
      super.onStop();
   }


   public void resetPresentation(final String frontImage, final String backImage)
   {
      setFrontImage(frontImage);
      setBackImage(backImage);
      //mFlipper.setDisplayedChild(0);
      mFlipper.setInAnimation(getContext(), mAnimations[0][0]);
      mFlipper.setOutAnimation(getContext(), mAnimations[0][1]);
      mFlipper.getInAnimation().setAnimationListener(mAnimationListener);

      mUiHandler.postDelayed(new Runnable()
      {
         @Override
         public void run()
         {
            mFlipper.setVisibility(View.VISIBLE);
         }
      },1000);
      
      mCounter = 1;
   }


   public void stopAutoPlay()
   {
      mFlipper.setVisibility(View.INVISIBLE);
      mFlipper.clearAnimation();
      mFlipper.getInAnimation().setAnimationListener(null);
   }

   public void showTest()
   {
      setFrontImage(R.drawable.testpic);
      mFlipper.setVisibility(View.VISIBLE);
   }

   public void hide()
   {
      mFlipper.setVisibility(View.INVISIBLE);
   }

   public void switchView(final String nextImageFile)
   {
      mNextImageFile = nextImageFile;
      mFlipper.showNext();
   }

   private void setup()
   {
      mUiHandler = new Handler();

      mImageViewA = (ImageView) findViewById(R.id.image_a);
      mImageViewB = (ImageView) findViewById(R.id.image_b);

      mFlipper = (ViewFlipper) findViewById(R.id.flipper);

      mAnimations = new int[NUM_OF_ANIMATIONS][2];

      mAnimations[0][0] = R.anim.slide_in_left;
      mAnimations[0][1] = R.anim.slide_out_right;
      mAnimations[1][0] = R.anim.flipp_in_v;
      mAnimations[1][1] = R.anim.flipp_out_v;
      mAnimations[2][0] = R.anim.slide_in_top;
      mAnimations[2][1] = R.anim.slide_out_bottom;
      mAnimations[3][0] = R.anim.slide_in_right;
      mAnimations[3][1] = R.anim.slide_out_left;
      mAnimations[4][0] = R.anim.flipp_in_h;
      mAnimations[4][1] = R.anim.flipp_out_h;
      mAnimations[5][0] = R.anim.grow_fade_in;
      mAnimations[5][1] = R.anim.shrink_fade_out;

      //mImageBitmapA = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888);
      //mImageBitmapB = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888);

      /*
      mFlipper.setInAnimation(getContext(), mAnimations[0][0]);
      mFlipper.setOutAnimation(getContext(), mAnimations[0][1]);

      findViewById(R.id.presentation).setOnClickListener(new View.OnClickListener()
      {
         @Override
         public void onClick(View v)
         {
            if (mNumberOfImages == 0) {
               return;
            }

            if (mIsPlaying) {
               stopAutoPlay();
            } else {
               startAutoPlay();
            }
         }
      });*/

   }


   private void setFrontImage(final int imageResource)
   {
      if (mFlipper.getDisplayedChild() == 0) {
         mImageViewA.setImageResource(imageResource);
      } else {
         mImageViewB.setImageResource(imageResource);
      }
   }

   private void setFrontImage(final String imagePath)
   {
      if (mFlipper.getDisplayedChild() == 0) {
         loadAndSetImage(mImageViewA, imagePath);
      } else {
         loadAndSetImage(mImageViewB, imagePath);
      }
   }

   private void setBackImage(final int imageResource)
   {
      if (mFlipper.getDisplayedChild() == 0) {
         mImageViewB.setImageResource(imageResource);
      } else {
         mImageViewA.setImageResource(imageResource);
      }
   }

   private void setBackImage(final String imagePath)
   {
      if (mFlipper.getDisplayedChild() == 0) {
         loadAndSetImage(mImageViewB, imagePath);
      } else {
         loadAndSetImage(mImageViewA, imagePath);
      }
   }

   private void loadAndSetImage(final ImageView imageView, final String imagePath)
   {
      new Thread(new Runnable()
      {
         @Override
         public void run()
         {
            //Bitmap inBitmap = (imageView == mImageViewA) ? mImageBitmapA : mImageBitmapB;
            final Bitmap imgBitmap = getBitmap(null/*inBitmap*/, imagePath, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
            mUiHandler.post(new Runnable()
            {
               @Override
               public void run()
               {
                  imageView.setImageBitmap(imgBitmap);
               }
            });
         }
      }).start();
   }

   /*
   private void loadAndSetImage(final ImageView imageView, final String imagePath)
   {
      imageView.setImageBitmap(getBitmap(imagePath, MAX_IMAGE_SIZE, MAX_IMAGE_SIZE));
   }*/


   private Bitmap getBitmap(Bitmap imageBitmap,String path, int reqWidth, int reqHeight)
   {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(path, options);

      //Log.d(LOG_TAG,"Img. Size: "+options.outWidth+"x"+options.outHeight);

      // Calculate inSampleSize
      options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

      // Decode bitmap with inSampleSize set
      options.inJustDecodeBounds = false;

      //options.inBitmap = imageBitmap;

      Bitmap outBitmap = BitmapFactory.decodeFile(path, options);
      int outWidth = (int) (mWidthScaleFactor * options.outWidth);

      return Bitmap.createScaledBitmap(outBitmap, outWidth, options.outHeight, true);
   }


   private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
   {
      // Raw height and width of image
      final int height = options.outHeight;
      final int width = options.outWidth;
      int inSampleSize = 1;

      if (height > reqHeight || width > reqWidth) {

         final int halfHeight = height / 2;
         final int halfWidth = width / 2;

         // Calculate the largest inSampleSize value that is a power of 2 and keeps both
         // height and width larger than the requested height and width.
         while ((halfHeight / inSampleSize) > reqHeight
               && (halfWidth / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
         }
      }

      return inSampleSize;
   }

   private Animation.AnimationListener mAnimationListener = new Animation.AnimationListener()
   {
      @Override
      public void onAnimationStart(Animation animation) {}

      @Override
      public void onAnimationRepeat(Animation animation) {}

      @Override
      public void onAnimationEnd(Animation animation)
      {
         setBackImage(mNextImageFile);
         mFlipper.setInAnimation(getContext(), mAnimations[mCounter % NUM_OF_ANIMATIONS][0]);
         mFlipper.setOutAnimation(getContext(), mAnimations[mCounter % NUM_OF_ANIMATIONS][1]);
         mFlipper.getInAnimation().setAnimationListener(mAnimationListener);
         mCounter++;
      }
   };

}
