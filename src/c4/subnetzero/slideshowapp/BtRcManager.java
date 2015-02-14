package c4.subnetzero.slideshowapp;


import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class BtRcManager
{
   public static final int REQUEST_ENABLE_BT = 1;
   public static final int REQUEST_DISCOVERABLE_BT = 2;
   public static final int STATE_CHANGED = 101;
   public static final int MESSAGE_RECEIVED = 102;
   public static final int STRING_RECEIVED = 103;

   private static final String LOG_TAG = "BtRcManager";
   private static final String UUID_STRING = "069c9397-7da9-4810-849c-f52f6b1deaf";
   private BluetoothAdapter mBluetoothAdapter;
   private volatile AcceptThread mAcceptThread;
   private volatile ConnectedThread mConnectedThread;
   private Context mContext;
   private Handler mUiHandler;
   private Callback mCallback;
   private volatile State mState;


   public enum State
   {
      IDLE,
      LISTEN,
      CONNECTED;
   }

   public BtRcManager(final Context context, final Handler uiHandler, final Callback callback)
   {
      mContext = context;
      mUiHandler = uiHandler;
      mCallback = callback;
      mState = State.IDLE;

      setup();
   }

   public State getState()
   {
      return mState;
   }

   public boolean isBtEnabled()
   {
      return mBluetoothAdapter != null && mBluetoothAdapter.isEnabled();
   }


   public void close()
   {
      if (mAcceptThread != null) {
         mAcceptThread.close();
      }

      if (mConnectedThread != null) {
         mConnectedThread.close();
      }
   }

   public void sendMessage(final RcMessage message)
   {
      final Gson gson = new Gson();
      Log.d(LOG_TAG, "TX: " + gson.toJson(message));
      sendString(gson.toJson(message));
   }

   public void sendString(final String msgStr)
   {
      byte[] pktData = null;
      ConnectedThread connectedThread;
      synchronized (this) {
         if (mState != State.CONNECTED) {
            return;
         }
         connectedThread = mConnectedThread;
      }

      try {
         pktData = msgStr.getBytes("UTF-8");
      } catch (UnsupportedEncodingException e) {
         /* EMPTY */
      }

      connectedThread.write(pktData);
   }


   public void startAcceptThread()
   {
      if (mConnectedThread != null) {
         mConnectedThread.close();
      }

      if (mAcceptThread == null) {
         mAcceptThread = new AcceptThread();
         mAcceptThread.start();
      }
   }

   private synchronized void setState(final State newSate)
   {
      mState = newSate;
      mUiHandler.obtainMessage(STATE_CHANGED, 0, 0, mState).sendToTarget();
   }


   private void setup()
   {
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

      if (mBluetoothAdapter == null) {
         Toast.makeText(mContext, "Bluetooth not supported", Toast.LENGTH_SHORT);
         return;
      }

      if (!mBluetoothAdapter.isEnabled()) {
         Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
         ((Activity) mContext).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
      }

      /*
      Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
      discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 180);
      ((Activity) mContext).startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_BT);
      */
   }


   private class AcceptThread extends Thread
   {
      private static final String LOG_TAG = "AcceptThread";
      private final BluetoothServerSocket mmServerSocket;

      public AcceptThread()
      {
         BluetoothServerSocket tmp = null;
         try {
            tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(LOG_TAG, UUID.fromString(UUID_STRING));
         } catch (Exception e) {
            Log.e(LOG_TAG, "Unable to get listen socket: ", e);
         }
         mmServerSocket = tmp;
      }

      @Override
      public void run()
      {
         BluetoothSocket connectionSocket;
         setName(LOG_TAG);

         if (mmServerSocket == null) {
            mAcceptThread = null;
            return;
         }

         try {
            setState(BtRcManager.State.LISTEN);
            connectionSocket = mmServerSocket.accept();
         } catch (Exception e) {
            Log.e(LOG_TAG, "Exception: ", e);
            setState(BtRcManager.State.IDLE);
            close();
            return;
         }

         setState(BtRcManager.State.CONNECTED);
         mConnectedThread = new ConnectedThread(connectionSocket);
         mConnectedThread.start();
         close();
      }

      public void close()
      {
         try {
            mmServerSocket.close();
         } catch (IOException e) {
            /* EMPTY */
         } finally {
            mAcceptThread = null;
         }
      }
   }

   private class ConnectedThread extends Thread
   {
      private static final String LOG_TAG = "ConnectedThread";
      private final BluetoothSocket mmSocket;
      private final InputStream mmInStream;
      private final OutputStream mmOutStream;

      public ConnectedThread(BluetoothSocket socket)
      {
         setName(LOG_TAG);
         mmSocket = socket;
         InputStream tmpIn = null;
         OutputStream tmpOut = null;

         try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
         } catch (IOException e) {
            /* EMPTY */
         }

         mmInStream = tmpIn;
         mmOutStream = tmpOut;
      }

      @Override
      public void run()
      {
         byte[] buffer = new byte[1024];
         int bytes = 0;

         if (mmInStream == null || mmOutStream == null) {
            close();
            return;
         }

         while (true) {
            try {
               bytes = mmInStream.read(buffer);
               parsePacket(new String(buffer, 0, bytes, "UTF-8"));
            } catch (Exception e) {
               Log.e(LOG_TAG, e.getMessage());
               close();
               if (e instanceof IOException) {
                  startAcceptThread();
               }
               break;
            }
         }
      }

      public void write(byte[] bytes)
      {
         if (mmSocket.isConnected()) {
            try {
               mmOutStream.write(bytes);
               mmOutStream.flush();
            } catch (IOException e) {
            /* EMPTY */
            }
         }
      }

      public void close()
      {
         try {
            mmSocket.close();
         } catch (Exception e) {
            /* EMPTY */
         } finally {
            mConnectedThread = null;
            setState(BtRcManager.State.IDLE);
         }
      }

      private void parsePacket(String msgStr)
      {
         Gson gson = new GsonBuilder().serializeNulls().create();

         try {
            msgStr = msgStr.trim();
            //Log.d(LOG_TAG, "RX: " + msgStr);
            RcMessage newMsg = gson.fromJson(msgStr, RcMessage.class);
            mUiHandler.obtainMessage(MESSAGE_RECEIVED, 0, 0, newMsg).sendToTarget();
         } catch (JsonSyntaxException e) {
            mUiHandler.obtainMessage(STRING_RECEIVED, 0, 0, msgStr).sendToTarget();
         }
      }

   }


   public interface Callback
   {

   }

}
