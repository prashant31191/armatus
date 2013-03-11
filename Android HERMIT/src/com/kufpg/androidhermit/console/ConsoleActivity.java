package com.kufpg.androidhermit.console;

import java.util.ArrayList;
import java.util.Arrays;

import com.djpsoft.moreDroid.ExpandoLayout;
import com.kufpg.androidhermit.MainActivity;
import com.kufpg.androidhermit.R;
import com.kufpg.androidhermit.StandardListActivity;
import com.kufpg.androidhermit.console.CommandDispatcher;
import com.kufpg.androidhermit.drag.DragLayout;
import com.kufpg.androidhermit.server.HermitServer;
import com.slidingmenu.lib.SlidingMenu;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnKeyListener;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Activity that displays an interactive, feature-rich
 * (at least it will be some day) HERMIT console.
 */
public class ConsoleActivity extends StandardListActivity {

	public static final String DRAG_LAYOUT = "drag_layout";
	public static final String TYPEFACE = "fonts/DroidSansMonoDotted.ttf";
	public static final String WHITESPACE = "\\s+";
	public static final int DEFAULT_FONT_SIZE = 15;
	public static final int PADDING = 5;
	public static final int ENTRY_CONSOLE_LIMIT = 100;
	public static final int ENTRY_COMMAND_LIMIT = 200;
	public static final int SELECT_ID = 19;
	public static final int DIALOG_ID = 20;

	private ListView mConsoleListView, mCommandListView;
	private ConsoleEntryAdapter mConsoleAdapter;
	private CommandHistoryAdapter mCommandAdapter;
	private ArrayList<ConsoleEntry> mConsoleEntries = new ArrayList<ConsoleEntry>(); 
	private ArrayList<String> mCommandEntries = new ArrayList<String>();
	private View mInputView, mRootView;
	private TextView mInputNum;
	private EditText mInputEditText;
	private SlidingMenu mSlidingMenu;
	private LinearLayout mExpandoLayoutGroup;
	private CommandDispatcher mDispatcher;
	private HermitServer mServer;
	private String mTempCommand;
	private boolean mInputEnabled = true;
	private boolean mSoftKeyboardVisible;
	private int mEntryCount = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.console_activity);

		//Ensures soft keyboard is opened on activity start
		setSoftKeyboardVisibility(mSoftKeyboardVisible = true);
		mRootView = ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
		mRootView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
			@Override
			//Detects whether soft keyboard is open or closed
			public void onGlobalLayout() {
				int rootHeight = mRootView.getRootView().getHeight();
				int heightDiff = rootHeight - mRootView.getHeight();
				if (heightDiff > rootHeight/3) { //This works on Nexus 7s, at the very least
					mSoftKeyboardVisible = true;
				} else {
					mSoftKeyboardVisible = false;
				}
			}
		});

		mDispatcher = new CommandDispatcher(this);
		mConsoleAdapter = new ConsoleEntryAdapter(this, mConsoleEntries);
		mConsoleListView = getListView();
		//TODO: Make mListView scroll when CommandIcons are dragged near boundaries
		registerForContextMenu(mConsoleListView);
		mInputView = getLayoutInflater().inflate(R.layout.console_input, null);
		mInputNum = (TextView) mInputView.findViewById(R.id.test_code_input_num);
		mInputNum.setText("hermit<" + mEntryCount + "> ");
		mInputEditText = (EditText) mInputView.findViewById(R.id.test_code_input_edit_text);
		mInputEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				//Closes SlidingMenu and scroll to bottom if user begins typing
				mSlidingMenu.showContent();
				mConsoleListView.post(new Runnable() {
					public void run() {
						//DON'T use scrollToBottom(); it will cause a strange jaggedy scroll effect
						setSelection(mConsoleListView.getCount() - 1);
					}
				});
			}
		});
		//Processes user input (and runs command, if input is a command) when Enter is pressed
		mInputEditText.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_ENTER
						&& event.getAction() == KeyEvent.ACTION_UP
						&& mInputEnabled) {
					String[] inputs = mInputEditText.getText().
							toString().trim().split(WHITESPACE);
					if (CommandDispatcher.isCommand(inputs[0])) {
						if (inputs.length == 1) {
							mDispatcher.runOnConsole(inputs[0]);
						} else {
							mDispatcher.runOnConsole(inputs[0], Arrays.copyOfRange
									(inputs, 1, inputs.length));
						}
					} else {
						addConsoleEntry(mInputEditText.getText().toString());
					}
					mInputEditText.setText("");
					return true;
				} else if (keyCode == KeyEvent.KEYCODE_ENTER
						&& event.getAction() == KeyEvent.ACTION_UP
						&& !mInputEnabled) {
					mInputEditText.setText("");
				}
				return false;
			}
		});
		mInputEditText.requestFocus();
		mConsoleListView.addFooterView(mInputView, null, false);
		setListAdapter(mConsoleAdapter); //MUST be called after addFooterView()
		updateConsoleEntries();

		//Typeface tinkering
		Typeface typeface = Typeface.createFromAsset(getAssets(), TYPEFACE);
		mInputNum.setTypeface(typeface);
		mInputEditText.setTypeface(typeface);

		mSlidingMenu = (SlidingMenu) findViewById(R.id.console_sliding_menu);
		refreshSlidingMenu();

		//Creates the sliding menu and iterates through the CommandLayouts
		//in command_expandable_menu.xml to link them to mSlidingMenu in this activity
		int commandLayoutCount = 0;
		mExpandoLayoutGroup = (LinearLayout) findViewById(R.id.expando_root_layout);
		//Increment by two, since each CommandLayout is separated by a divider View
		for(int i = 0; i < mExpandoLayoutGroup.getChildCount(); i += 2) {
			//Retrieve child 1 from ExpandoLayout because there is an implicit child 0 that is not viewable.
			//We found this out by accident and random number changing. We recommend not changing this code.
			commandLayoutCount += ((RelativeLayout) ((ExpandoLayout) mExpandoLayoutGroup.
					getChildAt(i)).getChildAt(1)).getChildCount(); 
		}	
		for (int i = 1; i <= commandLayoutCount; i++) {
			String layoutId = DRAG_LAYOUT + i;
			int resId = getResources().getIdentifier(layoutId, "id", "com.kufpg.androidhermit");
			((DragLayout) mSlidingMenu.getMenu().findViewById(resId)).setSlidingMenu(mSlidingMenu);
		}

		mCommandListView = (ListView)findViewById(R.id.History);
		mCommandAdapter = new CommandHistoryAdapter(this, mCommandEntries);
		mCommandListView.setAdapter(mCommandAdapter);
		updateCommandEntries();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("input", mInputEditText.getText().toString());
		outState.putInt("cursorPos", mInputEditText.getSelectionStart());
		outState.putInt("entryCount", mEntryCount);
		outState.putBoolean("softKeyboardVisibility", mSoftKeyboardVisible);
		outState.putSerializable("consoleEntries", mConsoleEntries);
		outState.putSerializable("commandEntries", mCommandEntries);
		if (mServer != null) {
			mServer.detach();
		}
		outState.putSerializable("server", mServer);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void onRestoreInstanceState(Bundle state) {
		super.onRestoreInstanceState(state);
		mInputEditText.setText(state.getString("input"));
		mInputEditText.setSelection(state.getInt("cursorPos"));
		mInputEditText.requestFocus();
		mEntryCount = state.getInt("entryCount");
		mSoftKeyboardVisible = state.getBoolean("softKeyboardVisibility");

		mConsoleEntries = (ArrayList<ConsoleEntry>) state.getSerializable("consoleEntries");
		mConsoleAdapter = new ConsoleEntryAdapter(this, mConsoleEntries);
		setListAdapter(mConsoleAdapter);
		updateConsoleEntries();

		mCommandEntries = (ArrayList<String>) state.getSerializable("commandEntries");
		mCommandAdapter = new CommandHistoryAdapter(this, mCommandEntries);
		mCommandListView.setAdapter(mCommandAdapter);
		updateCommandEntries();

		mServer = (HermitServer) state.getSerializable("server");
		if (mServer != null) {
			mServer.attach(this);
		}
	}

	@Override
	public void onBackPressed() {
		if (mServer != null) {
			mServer.cancel(true);
		}
		super.onBackPressed();
	}

	@Override
	public void onRestart() {
		/* Prevents HermitServer from throwing a NullPointerException
		 * (since ConsoleActivity doesn't get serialized) */
		if (mServer != null) {
			mServer.attach(this);
		}
		super.onRestart();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		if (info.position != mConsoleEntries.size() && //To prevent footer from spawning a ContextMenu
				!mConsoleEntries.get(info.position).getContents().isEmpty()) { //To prevent empty lines
			super.onCreateContextMenu(menu, v, menuInfo);

			if (mTempCommand != null) { //If user dragged CommandIcon onto entry
				menu.setHeaderTitle("Execute " + mTempCommand + " on...");
			} else { //If user long-clicked entry
				menu.setHeaderTitle(R.string.context_menu_title);
			}

			int order = 0;
			for (String keyword : mConsoleEntries.get(info.position).getKeywords()) {
				menu.add(0, v.getId(), order, keyword);
				order++;
			}
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item != null) {
			String keywordNStr = item.getTitle().toString();
			if (mTempCommand != null) { //If DragIcon command is run
				if (mInputEnabled) {
					mDispatcher.runOnConsole(mTempCommand, keywordNStr);
				} else {
					mInputEditText.setText(mTempCommand + " " + keywordNStr);
				}
			} else { //If Keyword command is run
				if (mInputEnabled) {
					mDispatcher.runKeywordCommand(keywordNStr, keywordNStr);
				} else {
					mInputEditText.setText(CommandDispatcher.getKeyword(keywordNStr)
							.getCommand().getCommandName() + " " + keywordNStr);
				}
			}
			mInputEditText.requestFocus(); //Prevents ListView from stealing focus
		}
		mTempCommand = null;
		return super.onContextItemSelected(item);
	}

	@Override
	public void onContextMenuClosed(Menu menu) {
		//Ensures that the temp variables do not persist to next context menu opening
		mTempCommand = null;
	}

	public void addCommandEntry(String commandName) {
		if (mCommandEntries.size() == 0
				|| commandName != mCommandEntries.get(0)) {
			mCommandEntries.add(0, commandName);
			updateCommandEntries();
		}
	}

	/**
	 * Adds a new entry to the console.
	 * @param contents The message to be shown in the entry.
	 */
	public void addConsoleEntry(String contents) {
		ConsoleEntry ce = new ConsoleEntry(contents, mEntryCount);
		mConsoleEntries.add(ce);
		updateConsoleEntries();
		mEntryCount++;
		mInputNum.setText("hermit<" + mEntryCount + "> ");
		scrollToBottom();
	}

	/**
	 * Appends a newline and newContents to the most recent entry.
	 * @param newContents The message to be appended to the entry.
	 */
	public void appendConsoleEntry(String newContents) {
		String contents = mConsoleEntries.get(mConsoleEntries.size() - 1).getContents();
		contents += "<br />" + newContents;
		mConsoleEntries.remove(mConsoleEntries.size() - 1);
		/* Use 1 less than mEntryCount, since we are retroactively modifying an entry after
		 * addEntry() was called (which incremented mEntryCount) */
		ConsoleEntry ce = new ConsoleEntry(contents, mEntryCount - 1);
		mConsoleEntries.add(ce);
		updateConsoleEntries();
		scrollToBottom();
	}

	/**
	 * Appends a progress spinner below the most recent entry's contents. Ideal for
	 * doing asynchronous tasks (such as HermitServer requests). The loading bar
	 * will be destroyed when appendConsoleEntry() is called.
	 */
	public void appendProgressSpinner() {
		String contents = mConsoleEntries.get(mConsoleEntries.size() - 1).getContents();
		mConsoleEntries.remove(mConsoleEntries.size() - 1);
		ConsoleEntry ce = new ConsoleEntry(contents, mEntryCount - 1, true);
		mConsoleEntries.add(ce);
		updateConsoleEntries();
		scrollToBottom();
	}

	/**
	 * Removes all console entries and resets the entry count.
	 */
	public void clear() {
		mConsoleEntries.clear();
		mEntryCount = 0;
		updateConsoleEntries();
	}

	public void disableInput() {
		mInputEnabled = false;
	}

	public void enableInput() {
		mInputEnabled = true;
	}

	/**
	 * Exits the console activity.
	 */
	public void exit() {
		finish();
		startActivity(new Intent(this, MainActivity.class));
	}

	public SlidingMenu getSlidingMenu() {
		return mSlidingMenu;
	}

	public void setServer(HermitServer server) {
		mServer = server;
	}

	/**
	 * Sets the name of the Command to be run on a keyword when selected from a ContextMenu.
	 * Intended to be used in conjunction with CommandIcon.
	 * @param commandName The name of the Command that will be run (if selected).
	 */
	public void setTempCommand(String commandName) {
		mTempCommand = commandName;
	}

	public void showSelectionDialog(int entryNum, String entryContents) {
		FragmentTransaction ft = getFragmentManager().beginTransaction();
		Fragment prev = getFragmentManager().findFragmentByTag("selecDialog");
		if (prev != null) {
			ft.remove(prev);
		}
		ft.addToBackStack(null);

		DialogFragment newFrag = ConsoleDialog.newInstance(entryNum, entryContents);
		newFrag.show(ft, "selecDialog");
		ft.commit();
	}

	/**
	 * Changes the SlidingMenu offset depending on which screen orientation is enabled.
	 * This probably works best on Nexus 7s.
	 */
	private void refreshSlidingMenu() {
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			mSlidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset_portrait);
		} else {
			mSlidingMenu.setBehindOffsetRes(R.dimen.slidingmenu_offset_landscape);
		}
	}

	/**
	 * Show the entry at the bottom of the console ListView.
	 */
	private void scrollToBottom() {
		mConsoleListView.post(new Runnable() {
			public void run() {
				setSelection(mConsoleListView.getCount());
				mConsoleListView.smoothScrollToPosition(mConsoleListView.getCount());
			}
		});
	}

	/**
	 * Shows or hides the soft keyboard.
	 * @param visibility Set to true to show soft keyboard, false to hide.
	 */
	private void setSoftKeyboardVisibility(boolean visibility) {
		if (visibility) {
			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		} else {
			getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
		}
	}

	/**
	 * Refreshes the console entries, removing excessive entries from the top if ENTRY_LIMIT is exceeded.
	 */
	private void updateConsoleEntries() {
		if (mConsoleEntries.size() > ENTRY_CONSOLE_LIMIT) {
			mConsoleEntries.remove(0);
		}
		mConsoleAdapter.notifyDataSetChanged();
		mInputNum.setText("hermit<" + mEntryCount + "> ");
	}

	private void updateCommandEntries() {
		if (mCommandEntries.size() > ENTRY_CONSOLE_LIMIT) {
			mCommandEntries.remove(0);
		}
		mCommandAdapter.notifyDataSetChanged();
	}

}