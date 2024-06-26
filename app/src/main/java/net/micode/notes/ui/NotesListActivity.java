/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.appwidget.AppWidgetManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Display;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.View.OnTouchListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.gtask.remote.GTaskSyncService;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.tool.BackupUtils;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.ui.NotesListAdapter.AppWidgetAttribute;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

import cn.pedant.SweetAlert.SweetAlertDialog;

//主界面，一进入就是这个界面
/**
 * @author k
 *
 */
public class NotesListActivity extends Activity implements OnClickListener, OnItemLongClickListener {   //没有用特定的标签加注释。。。感觉没有什么用
    private static final int FOLDER_NOTE_LIST_QUERY_TOKEN = 0;
 
    private static final int FOLDER_LIST_QUERY_TOKEN      = 1;
 
    private static final int MENU_FOLDER_DELETE = 0;
 
    private static final int MENU_FOLDER_VIEW = 1;
 
    private static final int MENU_FOLDER_CHANGE_NAME = 2;
 
    private static final String PREFERENCE_ADD_INTRODUCTION = "net.micode.notes.introduction";  //单行超过80个字符
 
    private enum ListEditState {
        NOTE_LIST, SUB_FOLDER, CALL_RECORD_FOLDER
    };
 
    private ListEditState mState;
 
    private BackgroundQueryHandler mBackgroundQueryHandler;
 
    private NotesListAdapter mNotesListAdapter;
 
    private ListView mNotesListView;
 
    private Button mAddNewNote;
 
    private boolean mDispatch;
 
    private int mOriginY;
 
    private int mDispatchY;
 
    private TextView mTitleBar;
 
    private long mCurrentFolderId;
 
    private ContentResolver mContentResolver;
 
    private ModeCallback mModeCallBack;
 
    private static final String TAG = "NotesListActivity";
 
    public static final int NOTES_LISTVIEW_SCROLL_RATE = 30;
 
    private NoteItemData mFocusNoteDataItem;
 
    private static final String NORMAL_SELECTION = NoteColumns.PARENT_ID + "=?";
 
    private static final String ROOT_FOLDER_SELECTION = "(" + NoteColumns.TYPE + "<>"
            + Notes.TYPE_SYSTEM + " AND " + NoteColumns.PARENT_ID + "=?)" + " OR ("
            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER + " AND "
            + NoteColumns.NOTES_COUNT + ">0)";
 
    private final static int REQUEST_CODE_OPEN_NODE = 102;
    private final static int REQUEST_CODE_NEW_NODE  = 103;
 
    @Override
    // 创建类
    /**
    * 覆盖基类方法，在应用程序启动时调用，用于初始化资源、设置布局，并检查是否需要向初次使用的用户展示介绍内容。
    */
    protected void onCreate(final Bundle savedInstanceState) {  //需要是final类型   根据程序上下文环境，Java关键字final有“这是无法改变的”或者“终态的”含义，它可以修饰非抽象类、非抽象类成员方法和变量。你可能出于两种理解而需要阻止改变：设计或效率。
        // final类不能被继承，没有子类，final类中的方法默认是final的。
        //final方法不能被子类的方法覆盖，但可以被继承。
        //final成员变量表示常量，只能被赋值一次，赋值后值不再改变。
        //final不能用于修饰构造方法。
        super.onCreate(savedInstanceState); // 调用父类的onCreate函数
        setContentView(R.layout.note_list);
        initResources();
 
        /**
         * Insert an introduction when user firstly use this application
         */
        setAppInfoFromRawRes();
    }
 
    @Override
    // 返回一些子模块完成的数据交给主Activity处理
    /*
    *  当从另一个Activity返回结果时调用，更新列表数据以反映用户在其他Activity中所做的更改
    * */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // 结果值 和 要求值 符合要求 
    	if (resultCode == RESULT_OK
                && (requestCode == REQUEST_CODE_OPEN_NODE || requestCode == REQUEST_CODE_NEW_NODE)) {
            mNotesListAdapter.changeCursor(null);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
            // 调用 Activity 的onActivityResult（）
        }
    }
    /**
     *  在首次启动应用时（从Shared Preferences中检查标志位PREFERENCE_ADD_INTRODUCTION），
     *  从raw资源目录读取名为"introduction"的文本文件，并将其内容作为一条工作笔记保存到数据库中。
     *  之后更新 Shared Preferences 标志位，表示已添加过介绍
     */
    private void setAppInfoFromRawRes() {
    	// Android平台给我们提供了一个SharedPreferences类，它是一个轻量级的存储类，特别适合用于保存软件配置参数。
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (!sp.getBoolean(PREFERENCE_ADD_INTRODUCTION, false)) {
            StringBuilder sb = new StringBuilder();
            InputStream in = null;
            try {
                // 把资源文件放到应用程序的/raw/raw下，那么就可以在应用中使用getResources获取资源后,
            	// 以openRawResource方法（不带后缀的资源文件名）打开这个文件。 
            	in = getResources().openRawResource(R.raw.introduction);
                if (in != null) {
                    InputStreamReader isr = new InputStreamReader(in);
                    BufferedReader br = new BufferedReader(isr);
                    char [] buf = new char[1024];  // 自行定义的数值，使用者不知道有什么意义
                    int len = 0;
                    while ((len = br.read(buf)) > 0) {
                        sb.append(buf, 0, len);
                    }
                } else {
                    Log.e(TAG, "Read introduction file error");
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
            
            // 创建空的WorkingNote
            WorkingNote note = WorkingNote.createEmptyNote(this, Notes.ID_ROOT_FOLDER,
                    AppWidgetManager.INVALID_APPWIDGET_ID, Notes.TYPE_WIDGET_INVALIDE,
                    ResourceParser.RED);
            note.setWorkingText(sb.toString());
            if (note.saveNote()) {
            	// 更新保存note的信息
                sp.edit().putBoolean(PREFERENCE_ADD_INTRODUCTION, true).commit();
            } else {
                Log.e(TAG, "Save introduction note error");
                return;
            }
        }
    }
    @Override
    /**
     * 覆盖父类方法，在活动开始时调用 startAsyncNotesListQuery() 来异步加载笔记列表数据
     */
    protected void onStart() {
        super.onStart();
        startAsyncNotesListQuery();
    }

    /**
     * 初始化活动所需的组件和变量，如Content Resolver、后台查询处理器（BackgroundQueryHandler）、根文件夹ID、ListView、Adapter等。
     *
     * 设置ListView的各种事件监听器，例如单击、长按事件处理，以及底部添加新笔记按钮的点击和触摸监听器。
     *
     * 创建了一个内部类 ModeCallback，实现了MultiChoiceModeListener和OnMenuItemClickListener接口，
     * 用于处理多选模式下的各种菜单项点击事件，如删除、移动笔记等
     */
    private void initResources() {
        mContentResolver = this.getContentResolver(); // 获取应用程序的数据，得到类似数据表的东西
        mBackgroundQueryHandler = new BackgroundQueryHandler(this.getContentResolver());
        mCurrentFolderId = Notes.ID_ROOT_FOLDER;
        
        // findViewById 是安卓编程的定位函数，主要是引用.R文件里的引用名
        mNotesListView = (ListView) findViewById(R.id.notes_list); // 绑定XML中的ListView，作为Item的容器
        mNotesListView.addFooterView(LayoutInflater.from(this).inflate(R.layout.note_list_footer, null),
                null, false);
        mNotesListView.setOnItemClickListener(new OnListItemClickListener());
        mNotesListView.setOnItemLongClickListener(this);
        mNotesListAdapter = new NotesListAdapter(this);
        mNotesListView.setAdapter(mNotesListAdapter);
        mAddNewNote = (Button) findViewById(R.id.btn_new_note);// 在activity中要获取该按钮
        mAddNewNote.setOnClickListener(this);
        mAddNewNote.setOnTouchListener(new NewNoteOnTouchListener());
        mDispatch = false;
        mDispatchY = 0;
        mOriginY = 0;
        mTitleBar = (TextView) findViewById(R.id.tv_title_bar);
        mState = ListEditState.NOTE_LIST;
        mModeCallBack = new ModeCallback();
    }
 
    // 继承自ListView.MultiChoiceModeListener 和 OnMenuItemClickListener

    /**
     * 该类主要用于显示和管理用户的笔记列表，支持多选操作（包括删除和移动笔记），
     * 同时确保在应用启动时能正确加载和处理资源文件中的介绍信息
     */
    private class ModeCallback implements ListView.MultiChoiceModeListener, OnMenuItemClickListener {
        private DropdownMenu mDropDownMenu;
        private ActionMode mActionMode;
        private MenuItem mMoveMenu;
 
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            getMenuInflater().inflate(R.menu.note_list_options, menu);
            menu.findItem(R.id.delete).setOnMenuItemClickListener(this);
            mMoveMenu = menu.findItem(R.id.move);
            if (mFocusNoteDataItem.getParentId() == Notes.ID_CALL_RECORD_FOLDER
                    || DataUtils.getUserFolderCount(mContentResolver) == 0) {
                mMoveMenu.setVisible(false);
            } else {
                mMoveMenu.setVisible(true);
                mMoveMenu.setOnMenuItemClickListener(this);
            }
            mActionMode = mode;
            mNotesListAdapter.setChoiceMode(true);
            mNotesListView.setLongClickable(false);
            mAddNewNote.setVisibility(View.GONE);
 
            View customView = LayoutInflater.from(NotesListActivity.this).inflate(
                    R.layout.note_list_dropdown_menu, null);
            mode.setCustomView(customView);
            mDropDownMenu = new DropdownMenu(NotesListActivity.this,
                    (Button) customView.findViewById(R.id.selection_menu),
                    R.menu.note_list_dropdown);
            mDropDownMenu.setOnDropdownMenuItemClickListener(new PopupMenu.OnMenuItemClickListener(){
                public boolean onMenuItemClick(final MenuItem item) {
                    mNotesListAdapter.selectAll(!mNotesListAdapter.isAllSelected());
                    updateMenu();
                    return true;
                }
 
            });
            return true;
        }
 
        // 更新菜单
        private void updateMenu() {
            int selectedCount = mNotesListAdapter.getSelectedCount();
            // Update dropdown menu
            String format = getResources().getString(R.string.menu_select_title, selectedCount);
            mDropDownMenu.setTitle(format); // 更改标题
            MenuItem item = mDropDownMenu.findItem(R.id.action_select_all);
            if (item != null) {
                if (mNotesListAdapter.isAllSelected()) {
                    item.setChecked(true);
                    item.setTitle(R.string.menu_deselect_all);
                } else {
                    item.setChecked(false);
                    item.setTitle(R.string.menu_select_all);
                }
            }
        }
 
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            // TODO Auto-generated method stub
            return false;
        }
 
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            // TODO Auto-generated method stub
            return false;
        }
 
        public void onDestroyActionMode(ActionMode mode) {
            mNotesListAdapter.setChoiceMode(false);
            mNotesListView.setLongClickable(true);
            mAddNewNote.setVisibility(View.VISIBLE);
        }
 
        public void finishActionMode() {
            mActionMode.finish();
        }
 
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id,
                boolean checked) {
            mNotesListAdapter.setCheckedItem(position, checked);
            updateMenu();
        }
 
        public boolean onMenuItemClick(MenuItem item) {
            if (mNotesListAdapter.getSelectedCount() == 0) {
                Toast.makeText(NotesListActivity.this, getString(R.string.menu_select_none),
                        Toast.LENGTH_SHORT).show();
                return true;
            }
 
            switch (item.getItemId()) {
                case R.id.delete:
                    new SweetAlertDialog(NotesListActivity.this, SweetAlertDialog.WARNING_TYPE)
                            .setTitleText("确定要删除该便签吗?")
                            .setContentText("注意不可恢复!")
                            .setCancelText("取消!")
                            .setConfirmText("确认删除!")
                            .showCancelButton(true)
                            .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                                @Override
                                public void onClick(SweetAlertDialog sDialog) {
                                    // reuse previous dialog instance, keep widget user state, reset them if you need
                                    sDialog.setTitleText("取消成功!")
                                            .setContentText("本次操作已取消")
                                            .setConfirmText("OK")
                                            .showCancelButton(false)
                                            .setCancelClickListener(null)
                                            .setConfirmClickListener(null)
                                            .changeAlertType(SweetAlertDialog.ERROR_TYPE);
                                }
                            })
                            .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                                @Override
                                public void onClick(SweetAlertDialog sDialog) {
                                    batchDelete();
                                    sDialog.setTitleText("删除成功!")
                                            .setContentText("该便签已删除成功!")
                                            .setConfirmText("OK")
                                            .showCancelButton(false)
                                            .setCancelClickListener(null)
                                            .setConfirmClickListener(null)
                                            .changeAlertType(SweetAlertDialog.SUCCESS_TYPE);
                                    finish();
                                }
                            })
                            .show();
                    break;
                case R.id.move:
                    startQueryDestinationFolders();
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    /**
     * @Description 这是一个实现 OnTouchListener 接口的内部类，
     * 主要负责处理添加新笔记按钮的触摸事件。
     * 其目的是在用户滑动屏幕时，如果触摸的是该按钮的透明部分，
     * 事件会被传递给位于按钮下方的笔记列表视图。
     * 这样设计是为了满足UI设计师的要求，使得用户可以通过滑动透明区域来滚动列表，
     * 同时保持添加新笔记按钮始终可见。
     */
    private class NewNoteOnTouchListener implements OnTouchListener {
 
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    Display display = getWindowManager().getDefaultDisplay();
                    int screenHeight = display.getHeight();
                    int newNoteViewHeight = mAddNewNote.getHeight();
                    int start = screenHeight - newNoteViewHeight;
                    int eventY = start + (int) event.getY();
                    /**
                     * Minus TitleBar's height
                     */
                    if (mState == ListEditState.SUB_FOLDER) {
                        eventY -= mTitleBar.getHeight();
                        start -= mTitleBar.getHeight();
                    }
                    /**
                     * HACKME:When click the transparent part of "New Note" button, dispatch
                     * the event to the list view behind this button. The transparent part of
                     * "New Note" button could be expressed by formula y=-0.12x+94锛圲nit:pixel锛�
                     * and the line top of the button. The coordinate based on left of the "New
                     * Note" button. The 94 represents maximum height of the transparent part.
                     * Notice that, if the background of the button changes, the formula should
                     * also change. This is very bad, just for the UI designer's strong requirement.
                     */
                    if (event.getY() < (event.getX() * (-0.12) + 94)) {
                        View view = mNotesListView.getChildAt(mNotesListView.getChildCount() - 1
                                - mNotesListView.getFooterViewsCount());
                        if (view != null && view.getBottom() > start
                                && (view.getTop() < (start + 94))) {
                            mOriginY = (int) event.getY();
                            mDispatchY = eventY;
                            event.setLocation(event.getX(), mDispatchY);
                            mDispatch = true;
                            return mNotesListView.dispatchTouchEvent(event);
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (mDispatch) {
                        mDispatchY += (int) event.getY() - mOriginY;
                        event.setLocation(event.getX(), mDispatchY);
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
                default: {
                    if (mDispatch) {
                        event.setLocation(event.getX(), mDispatchY);
                        mDispatch = false;
                        return mNotesListView.dispatchTouchEvent(event);
                    }
                    break;
                }
            }
            return false;
        }
 
    };

    /**
     * @Description 发起异步查询请求以获取指定文件夹下的笔记列表数据。
     * 使用 BackgroundQueryHandler 对象执行查询，并根据当前文件夹ID设置不同的查询条件。
     */
    private void startAsyncNotesListQuery() {
        String selection = (mCurrentFolderId == Notes.ID_ROOT_FOLDER) ? ROOT_FOLDER_SELECTION
                : NORMAL_SELECTION;
        mBackgroundQueryHandler.startQuery(FOLDER_NOTE_LIST_QUERY_TOKEN, null,
                Notes.CONTENT_NOTE_URI, NoteItemData.PROJECTION, selection, new String[] {
                    String.valueOf(mCurrentFolderId)
                }, NoteColumns.TYPE + " DESC," + NoteColumns.MODIFIED_DATE + " DESC");
    }

    /**
     * @Description 继承自 AsyncQueryHandler，用于在后台线程处理数据库查询。
     * 覆盖了onQueryComplete() 方法，在查询完成时根据传入的令牌（token）更新UI，
     * 比如替换笔记列表适配器的游标，或者处理查询文件夹列表的结果。
     */
    private final class BackgroundQueryHandler extends AsyncQueryHandler {
        public BackgroundQueryHandler(ContentResolver contentResolver) {
            super(contentResolver);
        }
 
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            switch (token) {
                case FOLDER_NOTE_LIST_QUERY_TOKEN:
                    mNotesListAdapter.changeCursor(cursor);
                    break;
                case FOLDER_LIST_QUERY_TOKEN:
                    if (cursor != null && cursor.getCount() > 0) {
                        showFolderListMenu(cursor);
                    } else {
                        Log.e(TAG, "Query folder failed");
                    }
                    break;
                default:
                    return;
            }
        }
    }

    /**
     * @Description 当需要显示文件夹选择对话框时，
     * 此方法构建并显示一个对话框，
     * 其中包含了文件夹列表。用户选择某个文件夹后，
     * 会批量移动所选笔记至该文件夹，并显示相应提示信息。
     */
    private void showFolderListMenu(Cursor cursor) {
        AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
        builder.setTitle(R.string.menu_title_select_folder);
        final FoldersListAdapter adapter = new FoldersListAdapter(this, cursor);
        builder.setAdapter(adapter, new DialogInterface.OnClickListener() {
 
            public void onClick(DialogInterface dialog, int which) {
                DataUtils.batchMoveToFolder(mContentResolver,
                        mNotesListAdapter.getSelectedItemIds(), adapter.getItemId(which));
                Toast.makeText(
                        NotesListActivity.this,
                        getString(R.string.format_move_notes_to_folder,
                                mNotesListAdapter.getSelectedCount(),
                                adapter.getFolderName(NotesListActivity.this, which)),
                        Toast.LENGTH_SHORT).show();
                mModeCallBack.finishActionMode();
            }
        });
        builder.show();
    }

    /**
     * @Description 启动一个新的 NoteEditActivity 以创建或编辑笔记，传递当前文件夹ID作为额外意图参数。
     */
    private void createNewNote() {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mCurrentFolderId);
        this.startActivityForResult(intent, REQUEST_CODE_NEW_NODE);
    }

    /**
     * @Description 异步任务，用于批量删除所选笔记。
     * 在非同步模式下直接删除笔记，在同步模式下将笔记移动到回收站文件夹。
     * 在删除/移动操作完成后，如果有相关的桌面小部件存在，
     * 更新这些小部件的状态。最后关闭多选模式。
     */
    private void batchDelete() {
        new AsyncTask<Void, Void, HashSet<AppWidgetAttribute>>() {
            protected HashSet<AppWidgetAttribute> doInBackground(Void... unused) {
                HashSet<AppWidgetAttribute> widgets = mNotesListAdapter.getSelectedWidget();
                if (!isSyncMode()) {
                    // if not synced, delete notes directly
                    if (DataUtils.batchDeleteNotes(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds())) {
                    } else {
                        Log.e(TAG, "Delete notes error, should not happens");
                    }
                } else {
                    // in sync mode, we'll move the deleted note into the trash
                    // folder
                    if (!DataUtils.batchMoveToFolder(mContentResolver, mNotesListAdapter
                            .getSelectedItemIds(), Notes.ID_TRASH_FOLER)) {
                        Log.e(TAG, "Move notes to trash folder error, should not happens");
                    }
                }
                return widgets;
            }
 
            @Override
            protected void onPostExecute(HashSet<AppWidgetAttribute> widgets) {
                if (widgets != null) {
                    for (AppWidgetAttribute widget : widgets) {
                        if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                                && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                            updateWidget(widget.widgetId, widget.widgetType);
                        }
                    }
                }
                mModeCallBack.finishActionMode();
            }
        }.execute();
    }

    /**
     * @Description 删除指定ID的文件夹。首先判断是否是根文件夹，
     * 如果是则不允许删除。接着收集该文件夹内所有笔记的ID，
     * 并根据同步模式决定是直接删除还是移至回收站文件夹。
     * 然后，更新与该文件夹关联的小部件。
     */
    private void deleteFolder(long folderId) {
        if (folderId == Notes.ID_ROOT_FOLDER) {
            Log.e(TAG, "Wrong folder id, should not happen " + folderId);
            return;
        }
 
        HashSet<Long> ids = new HashSet<Long>();
        ids.add(folderId);
        HashSet<AppWidgetAttribute> widgets = DataUtils.getFolderNoteWidget(mContentResolver,
                folderId);
        if (!isSyncMode()) {
            // if not synced, delete folder directly
            DataUtils.batchDeleteNotes(mContentResolver, ids);
        } else {
            // in sync mode, we'll move the deleted folder into the trash folder
            DataUtils.batchMoveToFolder(mContentResolver, ids, Notes.ID_TRASH_FOLER);
        }
        if (widgets != null) {
            for (AppWidgetAttribute widget : widgets) {
                if (widget.widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
                        && widget.widgetType != Notes.TYPE_WIDGET_INVALIDE) {
                    updateWidget(widget.widgetId, widget.widgetType);
                }
            }
        }
    }
 
    private void openNode(NoteItemData data) {
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(Intent.EXTRA_UID, data.getId());
        this.startActivityForResult(intent, REQUEST_CODE_OPEN_NODE);
    }
 
    private void openFolder(NoteItemData data) {
        mCurrentFolderId = data.getId();
        startAsyncNotesListQuery();
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mState = ListEditState.CALL_RECORD_FOLDER;
            mAddNewNote.setVisibility(View.GONE);
        } else {
            mState = ListEditState.SUB_FOLDER;
        }
        if (data.getId() == Notes.ID_CALL_RECORD_FOLDER) {
            mTitleBar.setText(R.string.call_record_folder_name);
        } else {
            mTitleBar.setText(data.getSnippet());
        }
        mTitleBar.setVisibility(View.VISIBLE);
    }
 
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_new_note:
                createNewNote();
                break;
            default:
                break;
        }
    }
 
    private void showSoftInput() {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }
 
    private void hideSoftInput(View view) {
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
 
    private void showCreateOrModifyFolderDialog(final boolean create) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_text, null);
        final EditText etName = (EditText) view.findViewById(R.id.et_foler_name);
        showSoftInput();
        if (!create) {
            if (mFocusNoteDataItem != null) {
                etName.setText(mFocusNoteDataItem.getSnippet());
                builder.setTitle(getString(R.string.menu_folder_change_name));
            } else {
                Log.e(TAG, "The long click data item is null");
                return;
            }
        } else {
            etName.setText("");
            builder.setTitle(this.getString(R.string.menu_create_folder));
        }
 
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                hideSoftInput(etName);
            }
        });
 
        final Dialog dialog = builder.setView(view).show();
        final Button positive = (Button)dialog.findViewById(android.R.id.button1);
        positive.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hideSoftInput(etName);
                String name = etName.getText().toString();
                if (DataUtils.checkVisibleFolderName(mContentResolver, name)) {
                    Toast.makeText(NotesListActivity.this, getString(R.string.folder_exist, name),
                            Toast.LENGTH_LONG).show();
                    etName.setSelection(0, etName.length());
                    return;
                }
                if (!create) {
                    if (!TextUtils.isEmpty(name)) {
                        ContentValues values = new ContentValues();
                        values.put(NoteColumns.SNIPPET, name);
                        values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                        values.put(NoteColumns.LOCAL_MODIFIED, 1);
                        mContentResolver.update(Notes.CONTENT_NOTE_URI, values, NoteColumns.ID
                                + "=?", new String[] {
                            String.valueOf(mFocusNoteDataItem.getId())
                        });
                    }
                } else if (!TextUtils.isEmpty(name)) {
                    ContentValues values = new ContentValues();
                    values.put(NoteColumns.SNIPPET, name);
                    values.put(NoteColumns.TYPE, Notes.TYPE_FOLDER);
                    mContentResolver.insert(Notes.CONTENT_NOTE_URI, values);
                }
                dialog.dismiss();
            }
        });
 
        if (TextUtils.isEmpty(etName.getText())) {
            positive.setEnabled(false);
        }
        /**
         * When the name edit text is null, disable the positive button
         */
        etName.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub
 
            }
 
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(etName.getText())) {
                    positive.setEnabled(false);
                } else {
                    positive.setEnabled(true);
                }
            }
 
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub
 
            }
        });
    }
 
    /* (non-Javadoc)
     * @see android.app.Activity#onBackPressed()
     * 按返回键时根据情况更改类中的数据
     */
    @Override
    public void onBackPressed() { switch (mState) {
            case SUB_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                startAsyncNotesListQuery();
                mTitleBar.setVisibility(View.GONE);
                break;
            case CALL_RECORD_FOLDER:
                mCurrentFolderId = Notes.ID_ROOT_FOLDER;
                mState = ListEditState.NOTE_LIST;
                mAddNewNote.setVisibility(View.VISIBLE);
                mTitleBar.setVisibility(View.GONE);
                startAsyncNotesListQuery();
                break;
            case NOTE_LIST:
                super.onBackPressed();
                break;
            default:
                break;
        }
    }
 
    /**
     * @param appWidgetId
     * @param appWidgetType
     * 根据不同类型的widget更新插件，通过intent传送数据
     */
    private void updateWidget(int appWidgetId, int appWidgetType) {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        if (appWidgetType == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);
        } else if (appWidgetType == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }
 
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
            appWidgetId
        });
 
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }
 
    /**
     * 声明监听器，建立菜单，包括名称，视图，删除操作，更改名称操作；
     */
    private final OnCreateContextMenuListener mFolderOnCreateContextMenuListener = new OnCreateContextMenuListener() {
        public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
            if (mFocusNoteDataItem != null) {
                menu.setHeaderTitle(mFocusNoteDataItem.getSnippet());
                menu.add(0, MENU_FOLDER_VIEW, 0, R.string.menu_folder_view);
                menu.add(0, MENU_FOLDER_DELETE, 0, R.string.menu_folder_delete);
                menu.add(0, MENU_FOLDER_CHANGE_NAME, 0, R.string.menu_folder_change_name);
            }
        }
    };
 
    @Override
    public void onContextMenuClosed(Menu menu) {
        if (mNotesListView != null) {
            mNotesListView.setOnCreateContextMenuListener(null);
        }
        super.onContextMenuClosed(menu);
    }
 
    /* (non-Javadoc)
     * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
     * 针对menu中不同的选择进行不同的处理，里面详细注释
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (mFocusNoteDataItem == null) {
            Log.e(TAG, "The long click data item is null");
            return false;
        }
        switch (item.getItemId()) {
            case MENU_FOLDER_VIEW:
                openFolder(mFocusNoteDataItem);//打开对应文件
                break;
            case MENU_FOLDER_DELETE:
                new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                        .setTitleText("确定要删除该组便签吗?")
                        .setContentText("注意不可恢复!")
                        .setCancelText("取消!")
                        .setConfirmText("确认删除!")
                        .showCancelButton(true)
                        .setCancelClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                // reuse previous dialog instance, keep widget user state, reset them if you need
                                sDialog.setTitleText("取消成功!")
                                        .setContentText("本次操作已取消")
                                        .setConfirmText("OK")
                                        .showCancelButton(false)
                                        .setCancelClickListener(null)
                                        .setConfirmClickListener(null)
                                        .changeAlertType(SweetAlertDialog.ERROR_TYPE);
                            }
                        })
                        .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                            @Override
                            public void onClick(SweetAlertDialog sDialog) {
                                deleteFolder(mFocusNoteDataItem.getId());
                                sDialog.setTitleText("删除成功!")
                                        .setContentText("该组便签已删除成功!")
                                        .setConfirmText("OK")
                                        .showCancelButton(false)
                                        .setCancelClickListener(null)
                                        .setConfirmClickListener(null)
                                        .changeAlertType(SweetAlertDialog.SUCCESS_TYPE);

                            }
                        })
                        .show();
                break;

//                AlertDialog.Builder builder = new AlertDialog.Builder(this);//设置确认是否删除的对话框
//                builder.setTitle(getString(R.string.alert_title_delete));
//                builder.setIcon(android.R.drawable.ic_dialog_alert);
//                builder.setMessage(getString(R.string.alert_message_delete_folder));
//                builder.setPositiveButton(android.R.string.ok,
//                        new DialogInterface.OnClickListener() {
//                            public void onClick(DialogInterface dialog, int which) {
//                                deleteFolder(mFocusNoteDataItem.getId());
//                            }
//                        });
//                builder.setNegativeButton(android.R.string.cancel, null);
//                builder.show();//显示对话框

            case MENU_FOLDER_CHANGE_NAME:
                showCreateOrModifyFolderDialog(false);
                break;
            default:
                break;
        }
 
        return true;
    }
 
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (mState == ListEditState.NOTE_LIST) {
            getMenuInflater().inflate(R.menu.note_list, menu);
            // set sync or sync_cancel
            menu.findItem(R.id.menu_sync).setTitle(
                    GTaskSyncService.isSyncing() ? R.string.menu_sync_cancel : R.string.menu_sync);
        } else if (mState == ListEditState.SUB_FOLDER) {
            getMenuInflater().inflate(R.menu.sub_folder, menu);
        } else if (mState == ListEditState.CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_record_folder, menu);
        } else {
            Log.e(TAG, "Wrong state:" + mState);
        }
        return true;
    }
 
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_new_folder: {
                showCreateOrModifyFolderDialog(true);
                break;
            }
            case R.id.menu_export_text: {
                exportNoteToText();
                break;
            }
            case R.id.menu_sync: {
                if (isSyncMode()) {
                    if (TextUtils.equals(item.getTitle(), getString(R.string.menu_sync))) {
                        GTaskSyncService.startSync(this);
                    } else {
                        GTaskSyncService.cancelSync(this);
                    }
                } else {
                    startPreferenceActivity();
                }
                break;
            }
            case R.id.menu_setting: {
                startPreferenceActivity();
                break;
            }
            case R.id.menu_new_note: {
                createNewNote();
                break;
            }
            case R.id.menu_search:
                onSearchRequested();
                break;
            default:
                break;
        }
        return true;
    }
 
    /* (non-Javadoc)
     * @see android.app.Activity#onSearchRequested()
     * 直接调用startSearch函数
     */
    @Override
    public boolean onSearchRequested() {
        startSearch(null, false, null /* appData */, false);
        return true;
    }
 
    /**
     * 函数功能：实现将便签导出到文本功能
     */
    private void exportNoteToText() {
        final BackupUtils backup = BackupUtils.getInstance(NotesListActivity.this);
        new AsyncTask<Void, Void, Integer>() {
 
            @Override
            protected Integer doInBackground(Void... unused) {
                return backup.exportToText();
            }
 
            @Override
            protected void onPostExecute(Integer result) {
                if (result == BackupUtils.STATE_SD_CARD_UNMOUONTED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_unmounted));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SUCCESS) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.success_sdcard_export));
                    builder.setMessage(NotesListActivity.this.getString(
                            R.string.format_exported_file_location, backup
                                    .getExportedTextFileName(), backup.getExportedTextFileDir()));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                } else if (result == BackupUtils.STATE_SYSTEM_ERROR) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(NotesListActivity.this);
                    builder.setTitle(NotesListActivity.this
                            .getString(R.string.failed_sdcard_export));
                    builder.setMessage(NotesListActivity.this
                            .getString(R.string.error_sdcard_export));
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.show();
                }
            }
 
        }.execute();
    }
 
    /**
     * @return
     * 功能：判断是否正在同步
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }
 
    /**
     * 功能：跳转到PreferenceActivity界面
     */
    private void startPreferenceActivity() {
        Activity from = getParent() != null ? getParent() : this;
        Intent intent = new Intent(from, NotesPreferenceActivity.class);
        from.startActivityIfNeeded(intent, -1);
    }
 
    /**
     * @author k
     * 函数功能：实现对便签列表项的点击事件（短按）
     */
    private class OnListItemClickListener implements OnItemClickListener {
 
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (view instanceof NotesListItem) {
                NoteItemData item = ((NotesListItem) view).getItemData();
                if (mNotesListAdapter.isInChoiceMode()) {
                    if (item.getType() == Notes.TYPE_NOTE) {
                        position = position - mNotesListView.getHeaderViewsCount();
                        mModeCallBack.onItemCheckedStateChanged(null, position, id,
                                !mNotesListAdapter.isSelectedItem(position));
                    }
                    return;
                }
 
                switch (mState) {
                    case NOTE_LIST:
                        if (item.getType() == Notes.TYPE_FOLDER
                                || item.getType() == Notes.TYPE_SYSTEM) {
                            openFolder(item);
                        } else if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in NOTE_LIST");
                        }
                        break;
                    case SUB_FOLDER:
                    case CALL_RECORD_FOLDER:
                        if (item.getType() == Notes.TYPE_NOTE) {
                            openNode(item);
                        } else {
                            Log.e(TAG, "Wrong note type in SUB_FOLDER");
                        }
                        break;
                    default:
                        break;
                }
            }
        }
 
    }
 
    /**
     * 查询目标文件
     */
    private void startQueryDestinationFolders() {
        String selection = NoteColumns.TYPE + "=? AND " + NoteColumns.PARENT_ID + "<>? AND " + NoteColumns.ID + "<>?";
        selection = (mState == ListEditState.NOTE_LIST) ? selection:
            "(" + selection + ") OR (" + NoteColumns.ID + "=" + Notes.ID_ROOT_FOLDER + ")";
 
        mBackgroundQueryHandler.startQuery(FOLDER_LIST_QUERY_TOKEN,
                null,
                Notes.CONTENT_NOTE_URI,
                FoldersListAdapter.PROJECTION,
                selection,
                new String[] {
                        String.valueOf(Notes.TYPE_FOLDER),
                        String.valueOf(Notes.ID_TRASH_FOLER),
                        String.valueOf(mCurrentFolderId)
                },
                NoteColumns.MODIFIED_DATE + " DESC");
    }
 
    /* (non-Javadoc)
     * @see android.widget.AdapterView.OnItemLongClickListener#onItemLongClick(android.widget.AdapterView, android.view.View, int, long)
     * 长按某一项时进行的操作
     * 如果长按的是便签，则通过ActionMode菜单实现；如果长按的是文件夹，则通过ContextMenu菜单实现；
     *        具体ActionMOde菜单和ContextMenu菜单的详细见精度笔记
     */
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        if (view instanceof NotesListItem) {
            mFocusNoteDataItem = ((NotesListItem) view).getItemData();
            if (mFocusNoteDataItem.getType() == Notes.TYPE_NOTE && !mNotesListAdapter.isInChoiceMode()) {
                if (mNotesListView.startActionMode(mModeCallBack) != null) {
                    mModeCallBack.onItemCheckedStateChanged(null, position, id, true);
                    mNotesListView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                } else {
                    Log.e(TAG, "startActionMode fails");
                }
            } else if (mFocusNoteDataItem.getType() == Notes.TYPE_FOLDER) {
                mNotesListView.setOnCreateContextMenuListener(mFolderOnCreateContextMenuListener);
            }
        }
        return false;
    }
}