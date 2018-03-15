package net.gimite.nativeexe;

import java.io.*;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import net.gimite.nativeexe.R;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.util.Log;


public class MainActivity extends Activity {
	public static final String TAG = "NativeExeActivity";
	private TextView outputView;
	private Button localRunButton;
	private EditText localInputEdit;
	private Handler handler = new Handler();
	private EditText remoteUriEdit;
	private Button remoteRunButton;

	private boolean mInputReady = false;
	private boolean bQuitFlag = false;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		outputView = (TextView) findViewById(R.id.outputView);
		localInputEdit = (EditText) findViewById(R.id.localPathEdit);
		localRunButton = (Button) findViewById(R.id.localRunButton);
		localRunButton.setOnClickListener(onLocalRunButtonClick);
		remoteUriEdit = (EditText) findViewById(R.id.urlEdit);
		remoteRunButton = (Button) findViewById(R.id.remoteRunButton);
		remoteRunButton.setOnClickListener(onRemoteRunButtonClick);

		outputView.setMovementMethod(new ScrollingMovementMethod()); //auto scroll
		Log.d(TAG, "created");
	}

	@Override
	protected void onPause() {
		super.onPause();
		mInputReady  = false;
		bQuitFlag = true;
	}

	private OnClickListener onLocalRunButtonClick = new OnClickListener() {
		public void onClick(View v) {
			mInputReady = true;
		}
	};

	private OnClickListener onRemoteRunButtonClick = new OnClickListener() {
		public void onClick(View v) {

			final String url = remoteUriEdit.getText().toString();
			final String localPath = "/data/data/net.gimite.nativeexe/a.out";
			
			Thread thread = new Thread(new Runnable() {
				public void run() {

					List<String> args = new ArrayList<String>();
					for(String item : url.split(" ")) {
						Log.d(TAG, "arg: " + item);
						args.add(item);
					}
					
					outputToUI("Downloading...", false);
					downloadFile(args.get(0), localPath);
					
					outputToUI("preparing ...", true);
					String ret = getResultFromExcuteCommand("/system/bin/chmod 744 " + localPath);
					Log.d(TAG, "chmod ret " + ret);
					
					args.set(0, localPath);
					try {
						runLocalExeWithArgs(args);
					}
					catch (IOException e) {
						outputToUI("run err " + e, true);
					}
				}
			});
			thread.start();
		}
	};

	private String getResultFromExcuteCommand(String command) {
		try {
			Process process = Runtime.getRuntime().exec(command);
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream()));
			int read;
			char[] buffer = new char[4096];
			StringBuilder output = new StringBuilder();
			while ((read = reader.read(buffer)) > 0) {
				output.append(buffer, 0, read);
			}
			reader.close();
			process.waitFor();
			return output.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	private void runLocalExeWithArgs(List<String> args) throws IOException {

		ProcessBuilder builder = new ProcessBuilder(args);
		builder.redirectErrorStream(true);
		Process process = builder.start();

		InputStream out = process.getInputStream();
		OutputStream in = process.getOutputStream();

		byte[] buffer = new byte[4096];
		bQuitFlag = false;
		while (isAlive(process) && !bQuitFlag) {
			int no = out.available();
			if (no > 0) {
				int n = out.read(buffer, 0, Math.min(no, buffer.length));
				String str =  new String(buffer, 0, n);
				Log.d(TAG, "OUT: " + str);
				outputToUI(str+"\n", true);
			}

			if(mInputReady) {
				mInputReady = false;

				String line = localInputEdit.getText().toString();
				int ni = line.length();
				if (ni > 0) {
					Log.d(TAG, "IN " + ni + ": "+line);
					line += "\n"; //flush stdin
					in.write(line.getBytes());
					in.flush();
				}
			}

			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
			}
		}
		process.destroy();

		try {
			Log.d(TAG, "EXIT: " + process.exitValue());
		}
		catch (IllegalThreadStateException e) {
			Log.d(TAG, "destroy process " +e);
			outputToUI("destroy process " +e, true);
		}
	}

	public static boolean isAlive(Process p) {
		try {
			p.exitValue();
			return false;
		}
		catch (IllegalThreadStateException e) {
			return true;
		}
	}

	private void downloadFile(String urlStr, String localPath) {
		try {
			Log.d(TAG, "download " + urlStr);
			if(urlStr.startsWith("/sdcard/")) {
				copyFileUsingStream(urlStr, localPath);
			}
			else {
				httpGet(urlStr, localPath);
			}
			Log.d(TAG, "finish to " + localPath);

		} catch (Exception e) {
			Log.e(TAG, "load fail e " + e);
		}
	}

	public static void httpGet(String urlStr, String localPath) throws IOException {
		URL url = new URL(urlStr);
		HttpURLConnection urlconn = (HttpURLConnection) url.openConnection();
		urlconn.setRequestMethod("GET");
		urlconn.setInstanceFollowRedirects(true);
		urlconn.connect();
		InputStream in = urlconn.getInputStream();


		FileOutputStream out = new FileOutputStream(localPath);
		int read;
		byte[] buffer = new byte[4096 * 10];
		while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
		out.close();
		in.close();
		urlconn.disconnect();
	}

	private void outputToUI(final String str, final boolean isAppend) {
		Runnable proc = new Runnable() {
			public void run() {
				StringBuilder sb  = new StringBuilder();
				if(isAppend){
					sb.append(outputView.getText());
				}
				sb.append(str);

				outputView.setText(sb.toString());
			}
		};
		handler.post(proc);
	}

	public static void copyFileUsingStream(String source, String dest) throws IOException {
		InputStream is = null;
		OutputStream os = null;
		try {
			File src = new File(source);
			File dst = new File(dest);

			is = new FileInputStream(src);
			os = new FileOutputStream(dst, false);

			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) > 0) {
				//Log.d(TAG, "read len " + length);
				os.write(buffer, 0, length);
			}
			is.close();
			os.close();
		}
		catch (Exception e) {
			Log.d(TAG, "copyFileUsingStream " +e);
		}
		
	}

}