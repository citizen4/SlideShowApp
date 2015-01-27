package c4.subnetzero.slideshowapp;

import android.app.Application;
import android.util.Log;

public class Main extends Application
{
   private static final String LOG_TAG = "Main";


   @Override
   public void onCreate()
   {
      Log.d(LOG_TAG, "onCreate()");
      super.onCreate();
   }

   @Override
   public void onTerminate()
   {
      Log.d(LOG_TAG,"onTerminate()");
      super.onTerminate();
   }
}
