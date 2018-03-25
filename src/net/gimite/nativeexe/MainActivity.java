package net.gimite.nativeexe;

import java.io.*;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import android.app.Activity;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextMenu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.util.Log;


public class MainActivity extends Activity {
	public static final String TAG = "NativeExeActivity";
	private TextView mOuptuView;
	private Handler mHandler = new Handler();
	private EditText mInputEdit;
	private Button mRunButton;

	private boolean mInputReady = false;
	private boolean mQuitFlag = false;
	private boolean mIsProcRunning = false;
	private String mLocalPath = null;
	private String lastInputStr = "";

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		mOuptuView = (TextView) findViewById(R.id.outputView);

		mInputEdit = (EditText) findViewById(R.id.inputEdit);
		mRunButton = (Button) findViewById(R.id.runButton);
		mRunButton.setOnClickListener(onRunButtonClick);

		mOuptuView.setMovementMethod(new ScrollingMovementMethod()); //auto scroll
		registerForContextMenu(mOuptuView);

		mLocalPath = this.getApplication().getFilesDir().getPath();
		Log.d(TAG, "created on " + mLocalPath);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		//user has long pressed your TextView
		menu.add(0, v.getId(), 0, "Copy");

		//cast the received View to TextView so that you can get its text
		TextView yourTextView = (TextView) v;

		//place your TextView's text in clipboard
		ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		clipboard.setText(yourTextView.getText());
	}

	@Override
	protected void onPause() {
		super.onPause();
		mInputReady  = false;
		Log.d(TAG, "on pause");

	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		mQuitFlag = true;
		Log.d(TAG, "on destroy set quit flag");
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.d(TAG, "on resume isProc running " + mIsProcRunning);
		if (mIsProcRunning) {
			if (lastInputStr.length() > 0) {
				mInputEdit.setText(lastInputStr);
			}
		}


	}

	private OnClickListener onRunButtonClick = new OnClickListener() {
		public void onClick(View v) {
			if (mIsProcRunning) {
				mInputReady = true;
			}
			else{

				String url = mInputEdit.getText().toString();
				if(url.trim().length() < 1) {return;}
				lastInputStr = url;

				mInputEdit.setText(""); //shift uri to ouptut
				outputToUI(url+"\n", false);

				Thread thread = new Thread(new Runnable() {
					public void run() {
						if (lastInputStr.charAt(0) != '/') {
							String ret = getResultFromExcuteCommand(lastInputStr);
							Log.d(TAG, "chmod ret " + ret);
							outputToUI(ret, false);
							return;
						}

						String localFileName = mLocalPath + "/a.out";

						List<String> args = new ArrayList<String>();
						for (String item : lastInputStr.split(" ")) {
							Log.d(TAG, "arg: " + item);
							args.add(item);
						}

						Boolean needLoad = true;
						try {

							needLoad = !compareByteContent(args.get(0), localFileName);

						} catch (IOException e) {
							needLoad = true;
						}


						if (needLoad) {
							outputToUI("Downloading...", true);
							downloadFile(args.get(0), localFileName);
							outputToUI("preparing ...", true);

							String ret = getResultFromExcuteCommand("/system/bin/chmod 744 " + localFileName);
							Log.d(TAG, "chmod ret " + ret);
						}


						args.set(0, localFileName);
						try {
							runLocalExeWithArgs(args);
						} catch (IOException e) {
							outputToUI("run err " + e, true);
							mIsProcRunning = false;
						}
					}
				});
				thread.start();
			}
		}
	};

	private String getResultFromExcuteCommand(String command) {
		try {
			String[] envp = null;
			Process process = Runtime.getRuntime().exec(command, envp, new File(mLocalPath));

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
		for(String a : args) {
			Log.d(TAG, "exe args " + a);
		}

		ProcessBuilder builder = new ProcessBuilder(args);
		builder.redirectErrorStream(true);
		builder.directory(new File(mLocalPath));
		Map<String, String> env = builder.environment();
		Log.d(TAG, "HOME="+env.get("HOME")+ " to " + mLocalPath);
		env.put("HOME", mLocalPath);

		Process process = builder.start();

		InputStream out = process.getInputStream();
		OutputStream in = process.getOutputStream();

		byte[] buffer = new byte[4096];
		mIsProcRunning = true;
		mQuitFlag = false;
		lastInputStr = "";
		String inputStr = mInputEdit.getText().toString();
		while (isAlive(process) && !mQuitFlag) {
			StringBuilder sb  = new StringBuilder();
			int no = 0;
			do {
				no = out.available();
				if (no > 0) {
					int n = out.read(buffer, 0, Math.min(no, buffer.length));
					sb.append(new String(buffer, 0, n));
				}
			}while(no > 0);
			if (sb.length() > 0) {
				Log.d(TAG, "OUT: '" + sb.toString() + "'ENDOUT");
				outputToUI(sb.toString() + "\n", true);
			}

			if(mInputReady) {
				mInputReady = false;

				inputStr = mInputEdit.getText().toString();
				int ni = inputStr.length();
				if (ni > 0) {
					Log.d(TAG, "IN " + ni + ": '"+inputStr + "'ENDIN");
					lastInputStr = inputStr;

					inputStr += "\n"; //flush stdin
					in.write(inputStr.getBytes());
					in.flush();

					outputToUI(inputStr, true);

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
			//outputToUI("destroy process " +e, true);
		}
		mIsProcRunning = false;
		outputToUI("process destroyed", true);
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
			Log.d(TAG, "ext storage " + Environment.getExternalStorageDirectory().getPath());

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
					String origStr = mOuptuView.getText().toString();
					String tmp = origStr.trim();

					if(tmp.charAt(tmp.length()-1) == '>') //is prompt
						sb.append(tmp + " ");
					else
						sb.append(origStr);
				}
				sb.append(str);

				mOuptuView.setText(sb.toString());
			}
		};
		mHandler.post(proc);
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

	public static boolean compareByteContent(String file1, String file2) throws IOException
	{
		File f1 = new File(file1);
		File f2 = new File(file2);
		FileInputStream fis1 = new FileInputStream (f1);
		FileInputStream fis2 = new FileInputStream (f2);

		if (f1.length() != f2.length()) {
			return false;
		}

		int n = 0;
		byte[] b1;
		byte[] b2;
		while ((n = fis1.available()) > 0) {
			if (n > 4096) n = 4096;
			b1 = new byte[n];
			b2 = new byte[n];
			fis1.read(b1);
			fis2.read(b2);
			if (!Arrays.equals(b1, b2)) {
				return false;
			}
		}

		return true;
	}

}