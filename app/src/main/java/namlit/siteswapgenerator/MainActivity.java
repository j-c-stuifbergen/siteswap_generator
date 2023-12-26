/*
* Siteswap Generator: Android App for generating juggling siteswaps
* Copyright (C) 2017 Tilman Sinning
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package namlit.siteswapgenerator;

import android.Manifest;
import android.app.AlertDialog;
import androidx.sqlite.db.SimpleSQLiteQuery;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.text.TextWatcher;
import android.text.Editable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.channels.FileChannel;
import java.util.List;

import siteswaplib.*;

public class MainActivity extends AppCompatActivity
        implements AddFilterDialog.FilterDialogListener,
        LoadGenerationParametersDialog.UpdateGenerationParameters {

    final static int PATTERN_FILTER_ITEM_NUMBER = 0;
    final static int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    final static int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 2;

    private FilterList mFilterList;

    private int mNumberOfObjects;
    private int mPeriodLength;
    private int mMaxThrow;
    private int mMinThrow;
    private int mNumberOfJugglers;
    private int mMaxResults;
    private int mTimeout;
    private boolean mIsSyncPattern;
    private boolean mIsRandomGenerationMode;
    private boolean mIsZips;
    private boolean mIsZaps;
    private boolean mIsHolds;
    private int mFilterSpinnerPosition;
    private EditText mNumberOfObjectsEditText;
    private EditText mPeriodLengthEditText;
    private EditText mMaxThrowEditText;
    private EditText mMinThrowEditText;
    private EditText mNumberOfJugglersEditText;
    private EditText mMaxResultsEditText;
    private EditText mTimeoutEditText;
    private CheckBox mSyncModeCheckbox;
    private CheckBox mRandomGenerationModeCheckbox;
    private CheckBox mZipsCheckbox;
    private CheckBox mZapsCheckbox;
    private CheckBox mHoldsCheckbox;
    private Spinner mFilterTypeSpinner;
    private NonScrollListView mFilterListView;
    private ArrayAdapter<Filter> mFilterListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        populateDatabaseWithDefaultGenerationParameters();

        mNumberOfObjectsEditText = (EditText) findViewById(R.id.number_of_objects);
        mPeriodLengthEditText = (EditText) findViewById(R.id.period_length);
        mMaxThrowEditText = (EditText) findViewById(R.id.max_throw);
        mMinThrowEditText = (EditText) findViewById(R.id.min_throw);
        mNumberOfJugglersEditText = (EditText) findViewById(R.id.number_of_jugglers);
        mMaxResultsEditText = (EditText) findViewById(R.id.max_results);
        mTimeoutEditText = (EditText) findViewById(R.id.timeout);
        mZipsCheckbox       = (CheckBox) findViewById(R.id.include_zips_checkbox);
        mZapsCheckbox       = (CheckBox) findViewById(R.id.include_zaps_checkbox);
        mHoldsCheckbox      = (CheckBox) findViewById(R.id.include_holds_checkbox);
        mFilterTypeSpinner  = (Spinner) findViewById(R.id.filter_type_spinner);
        mFilterListView     = (NonScrollListView) findViewById(R.id.filter_list);
        mSyncModeCheckbox   = (CheckBox) findViewById(R.id.sync_mode_checkbox);
        mRandomGenerationModeCheckbox = (CheckBox) findViewById(R.id.random_generation_mode_checkbox);

        mFilterList = new FilterList();

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        mNumberOfObjects  = sharedPref.getInt(getString(R.string.main_activity__settings_number_of_objects),  7);
        mPeriodLength     = sharedPref.getInt(getString(R.string.main_activity__settings_period_length),      5);
        mMaxThrow         = sharedPref.getInt(getString(R.string.main_activity__settings_max_throw),         10);
        mMinThrow         = sharedPref.getInt(getString(R.string.main_activity__settings_min_throw),          2);
        mNumberOfJugglers = sharedPref.getInt(getString(R.string.main_activity__settings_number_of_jugglers), 2);
        mMaxResults       = sharedPref.getInt(getString(R.string.main_activity__settings_max_results),      100);
        mTimeout          = sharedPref.getInt(getString(R.string.main_activity__settings_timeout),            5);
        mIsZips       = sharedPref.getBoolean(getString(R.string.main_activity__settings_is_zips), true);
        mIsZaps       = sharedPref.getBoolean(getString(R.string.main_activity__settings_is_zaps), false);
        mIsHolds      = sharedPref.getBoolean(getString(R.string.main_activity__settings_is_holds), false);
        mIsSyncPattern = sharedPref.getBoolean(
                getString(R.string.main_activity__settings_is_sync_pattern), false);
        mIsRandomGenerationMode = sharedPref.getBoolean(
                getString(R.string.main_activity__settings_is_random_generation_mode), false);
        mFilterSpinnerPosition = sharedPref.getInt(getString(R.string.main_activity__settings_filter_spinner_position), 0);
        String serializedFilterList = sharedPref.getString(getString(R.string.main_activity__settings_filter_list), "");

        if (serializedFilterList != "") {
            try {
                byte b[] = Base64.decode(serializedFilterList, Base64.DEFAULT);
                ByteArrayInputStream bi = new ByteArrayInputStream(b);
                ObjectInputStream si = new ObjectInputStream(bi);
                mFilterList = (FilterList) si.readObject();
                si.close();
            } catch (Exception e) {
                Toast.makeText(this, getString(R.string.main_activity__deserialization_error_toast),
                        Toast.LENGTH_SHORT).show();
            }
        }


        mNumberOfObjectsEditText.setText(String.valueOf(mNumberOfObjects));
        mPeriodLengthEditText.setText(String.valueOf(mPeriodLength));
        mMaxThrowEditText.setText(String.valueOf(mMaxThrow));
        mMinThrowEditText.setText(String.valueOf(mMinThrow));
        mNumberOfJugglersEditText.setText(String.valueOf(mNumberOfJugglers));
        mMaxResultsEditText.setText(String.valueOf(mMaxResults));
        mTimeoutEditText.setText(String.valueOf(mTimeout));
        mSyncModeCheckbox.setChecked(mIsSyncPattern);
        mRandomGenerationModeCheckbox.setChecked(mIsRandomGenerationMode);
        mZipsCheckbox.setChecked(mIsZips);
        mZapsCheckbox.setChecked(mIsZaps);
        mHoldsCheckbox.setChecked(mIsHolds);
        mFilterTypeSpinner.setSelection(mFilterSpinnerPosition);


        mFilterListAdapter = new ArrayAdapter<Filter>(
                this, android.R.layout.simple_list_item_1, mFilterList);
        mFilterListView.setAdapter(mFilterListAdapter);
        mFilterListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Filter filter = (Filter) parent.getItemAtPosition(position);

                if (!updateFromTextEdits())
                    return;
                if (filter instanceof NumberFilter) {

                        new NumberFilterDialog().show(getSupportFragmentManager(),
                                getString(R.string.number_filter__dialog_tag),
                                mMinThrow, mMaxThrow, mPeriodLength,
                                getNumberOfSynchronousHands(), filter);
                }
                else if (filter instanceof PatternFilter)
                    new PatternFilterDialog().show(getSupportFragmentManager(),
                            getString(R.string.pattern_filter__dialog_tag), mNumberOfJugglers, filter);

            }
        });

        updateAutoFilters();

        mNumberOfJugglersEditText.addTextChangedListener(new TextWatcher() {
            private boolean was_invalid = false;
            private int invalid_filter_list_length = 0;
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                // Add Default Filters, if new text is not empty
                if (count != 0 || start != 0)
                    updateAutoFilters();
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Remove Default Filters, if old text was not empty
                if (count == 0 && start == 0) {
                    return;
                }
                updateFromTextEdits();
                mFilterList.removeDefaultFilters(mNumberOfJugglers, getNumberOfSynchronousHands());
                mFilterList.addZips(mNumberOfJugglers, getNumberOfSynchronousHands());
                mFilterList.addZaps(mNumberOfJugglers, getNumberOfSynchronousHands());
                mFilterList.addHolds(mNumberOfJugglers, getNumberOfSynchronousHands());
                mFilterListAdapter.notifyDataSetChanged();
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public GenerationParameterEntity build7ClubDefaultGenerationEntity() {
        GenerationParameterEntity entity = new GenerationParameterEntity();
        entity.setName("Default: 7 clubs period 5");
        entity.setNumberOfObjects(7);
        entity.setPeriodLength(5);
        entity.setMaxThrow(10);
        entity.setMinThrow(2);
        entity.setNumberOfJugglers(2);
        entity.setMaxResults(100);
        entity.setTimeout(5);
        entity.setSynchronous(false);
        entity.setRandomMode(false);
        entity.setZips(true);
        entity.setZaps(false);
        entity.setHolds(false);
        FilterList list = new FilterList();
        list.addDefaultFilters(2, 2, 1);
        list.removeZaps(2, 1);
        list.removeHolds(2, 1);
        entity.setFilterList(list);
        return entity;
    }

    public void populateDatabaseWithDefaultGenerationParameters() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AppDatabase db = AppDatabase.getAppDatabase(getApplicationContext());
                    List<GenerationParameterEntity> entities =
                            db.generationParameterDao().getAllGenerationParameters();
                    if (entities.isEmpty()) {
                        db.generationParameterDao().insertGenerationParameters(
                                build7ClubDefaultGenerationEntity()
                        );
                    }
                } catch (android.database.sqlite.SQLiteConstraintException e) {
                }
            }
        }).start();
    }

    public void saveGenerationParameters() {
        GenerationParameterEntity entity = new GenerationParameterEntity();
        if (!updateFromTextEdits())
            return;
        entity.setNumberOfObjects(mNumberOfObjects);
        entity.setPeriodLength(mPeriodLength);
        entity.setMaxThrow(mMaxThrow);
        entity.setMinThrow(mMinThrow);
        entity.setNumberOfJugglers(mNumberOfJugglers);
        entity.setMaxResults(mMaxResults);
        entity.setTimeout(mTimeout);
        entity.setSynchronous(mIsSyncPattern);
        entity.setRandomMode(mIsRandomGenerationMode);
        entity.setZips(mIsZips);
        entity.setZaps(mIsZaps);
        entity.setHolds(mIsHolds);
        entity.setFilterList(mFilterList);
        new SaveGenerationParametersDialog().show(getSupportFragmentManager(),
                getString(R.string.save_generation_parameters__dialog_tag), entity);
    }

    public void updateGenerationParameters(GenerationParameterEntity generationParameters) {

        mNumberOfObjectsEditText.setText(String.valueOf(generationParameters.getNumberOfObjects()));
        mPeriodLengthEditText.setText(String.valueOf(generationParameters.getPeriodLength()));
        mMaxThrowEditText.setText(String.valueOf(generationParameters.getMaxThrow()));
        mMinThrowEditText.setText(String.valueOf(generationParameters.getMinThrow()));
        mNumberOfJugglersEditText.setText(String.valueOf(generationParameters.getNumberOfJugglers()));
        mMaxResultsEditText.setText(String.valueOf(generationParameters.getMaxResults()));
        mTimeoutEditText.setText(String.valueOf(generationParameters.getTimeout()));
        mSyncModeCheckbox.setChecked(generationParameters.isSynchronous());
        mRandomGenerationModeCheckbox.setChecked(generationParameters.isRandomMode());
        mZipsCheckbox.setChecked(generationParameters.isZips());
        mZapsCheckbox.setChecked(generationParameters.isZaps());
        mHoldsCheckbox.setChecked(generationParameters.isHolds());
        mFilterList.fromParsableString(generationParameters.getFilterListString());
        mFilterListAdapter.notifyDataSetChanged();
    }

    public void loadGenerationParameters() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppDatabase db = AppDatabase.getAppDatabase(getApplicationContext());
                final List<GenerationParameterEntity> entities =
                        db.generationParameterDao().getAllGenerationParameters();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new LoadGenerationParametersDialog().show(getSupportFragmentManager(),
                                getString(R.string.load_generation_parameters__dialog_tag), entities);
                    }
                });
            }
        }).start();
    }

    public void deleteGenerationParameters() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppDatabase db = AppDatabase.getAppDatabase(getApplicationContext());
                final List<GenerationParameterEntity> entities =
                        db.generationParameterDao().getAllGenerationParameters();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new DeleteGenerationParametersDialog().show(getSupportFragmentManager(),
                                getString(R.string.delete_generation_parameters__dialog_tag), entities);
                    }
                });
            }
        }).start();
    }

    private File getDatabaseBackupFile() {
        return new File(getExternalFilesDir(null), "backup_" + AppDatabase.database_name);
    }

    public void exportAppDatabase() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                AppDatabase.getAppDatabase(getApplicationContext()).siteswapDao().checkpoint(new SimpleSQLiteQuery("pragma wal_checkpoint(full)"));
                File source_path = getDatabasePath(AppDatabase.database_name);
                File dest_path = getDatabaseBackupFile();
                COPY_FILE_STATE status = copyFile(source_path, dest_path);
                final String user_msg;
                switch (status) {
                    case SUCCESS:
                        user_msg = String.format(getString(
                                R.string.main_activity__successfully_exported_database_msg), dest_path.toString());
                        break;
                    case FILE_NOT_FOUND:
                        user_msg = String.format(getString(R.string.main_activity__file_not_found_toast), dest_path.toString());
                        break;
                    case IO_ERROR:
                        user_msg = getString(R.string.main_activity__io_exeption_toast);
                        break;
                    default:
                        user_msg = "Error: unknown return value of copy_file";
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showMessageDialog(user_msg);
                    }
                });
            }
        }).start();
    }

    public void importAppDatabase() {

        confirmDatabaseImportDialog();
    }

    private void copyAppDatabaseFromExternalStorage() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                AppDatabase.getAppDatabase(getApplicationContext()).siteswapDao().checkpoint(new SimpleSQLiteQuery("pragma wal_checkpoint(full)"));
                AppDatabase.destroyInstance();
                File source_path = getDatabaseBackupFile();
                File dest_path = getDatabasePath(AppDatabase.database_name);
                File dbwal = new File(getDatabasePath(AppDatabase.database_name).getAbsolutePath() + "-wal");
                File dbshm = new File(getDatabasePath(AppDatabase.database_name).getAbsolutePath() + "-shm");
                dbwal.delete();
                dbshm.delete();
                COPY_FILE_STATE status = copyFile(source_path, dest_path);
                final String user_msg;
                switch (status) {
                    case SUCCESS:
                        user_msg = String.format(getString(
                                R.string.main_activity__successfully_imported_database_msg), source_path.toString());
                        break;
                    case FILE_NOT_FOUND:
                        user_msg = String.format(getString(R.string.main_activity__file_not_found_toast), source_path.toString());
                        break;
                    case IO_ERROR:
                        user_msg = getString(R.string.main_activity__io_exeption_toast);
                        break;
                    default:
                        user_msg = "Error: unknown return value of copy_file";
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), user_msg,
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private enum COPY_FILE_STATE {SUCCESS, IO_ERROR, FILE_NOT_FOUND};
    public COPY_FILE_STATE copyFile(File source, File destination) {
        //Toast.makeText(this, source.toString() + "\n" + destination.toString(),
        //        Toast.LENGTH_LONG).show();

        FileChannel sourceChannel;
        FileChannel destinationChannel;
        try {
            sourceChannel = new FileInputStream(source).getChannel();
            destinationChannel = new FileOutputStream(destination).getChannel();
            try {
                destinationChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
                destinationChannel.close();
                sourceChannel.close();
            } catch (IOException e) {
                return COPY_FILE_STATE.IO_ERROR;
            }
        } catch (FileNotFoundException e) {
            return COPY_FILE_STATE.FILE_NOT_FOUND;
        }
        return COPY_FILE_STATE.SUCCESS;
    }

    private void showMessageDialog(String message)
    {
        try
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            builder.setMessage(message);
            builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();

        } catch (Throwable t) {

            t.printStackTrace();
        }
    }

    private void confirmDatabaseImportDialog()
    {

        try
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            String source = getDatabaseBackupFile().toString();
            builder.setMessage(getString(R.string.main_activity__import_database_confirmation, source));
            builder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });
            builder.setPositiveButton(getString(R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    copyAppDatabaseFromExternalStorage();
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();

        } catch (Throwable t) {

            t.printStackTrace();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_load_generation_parameters)
        {
            loadGenerationParameters();
        }
        else if (id == R.id.action_save_generation_parameters)
        {
            saveGenerationParameters();
        }
        else if (id == R.id.action_delete_generation_parameters)
        {
            deleteGenerationParameters();
        }
        else if (id == R.id.action_named_siteswaps)
        {
            showNamedSiteswaps();
        }
        else if (id == R.id.action_export_database)
        {
            exportAppDatabase();
        }
        else if (id == R.id.action_import_database)
        {
            importAppDatabase();
        }
        else if (id == R.id.action_favorites)
        {
            favorites();
        }
        else if (id == R.id.action_about)
        {
            showAboutDialog();
        }
        else if (id == R.id.action_help) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(Html.fromHtml(getString(R.string.help_activity__help_html_text)))
                    .setNeutralButton(getString(R.string.back), null);
            builder.create().show();
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean updateFromTextEdits() {

        try {
            mNumberOfObjects = Integer.valueOf(mNumberOfObjectsEditText.getText().toString());
            mPeriodLength = Integer.valueOf(mPeriodLengthEditText.getText().toString());
            mMaxThrow = Integer.valueOf(mMaxThrowEditText.getText().toString());
            mMinThrow = Integer.valueOf(mMinThrowEditText.getText().toString());
            mNumberOfJugglers = Integer.valueOf(mNumberOfJugglersEditText.getText().toString());
            mMaxResults = Integer.valueOf(mMaxResultsEditText.getText().toString());
            mTimeout = Integer.valueOf(mTimeoutEditText.getText().toString());
            mIsSyncPattern = mSyncModeCheckbox.isChecked();
            mIsRandomGenerationMode = mRandomGenerationModeCheckbox.isChecked();
            mIsZips = mZipsCheckbox.isChecked();
            mIsZaps = mZapsCheckbox.isChecked();
            mIsHolds = mHoldsCheckbox.isChecked();
            mFilterSpinnerPosition = mFilterTypeSpinner.getSelectedItemPosition();

            if (mPeriodLength < 1)
                throw new IllegalArgumentException(getString(R.string.main_activity__invalid_period_length));

            if (mNumberOfObjects < 1)
                throw new IllegalArgumentException(getString(R.string.main_activity__invalid_number_of_objects));

            if (mNumberOfJugglers < 1 || mNumberOfJugglers > 10)
                throw new IllegalArgumentException(getString(R.string.main_activity__invalid_number_of_jugglers));

            if (mMaxThrow < mNumberOfObjects)
                throw new IllegalArgumentException(getString(R.string.main_activity__invalid_max_throw_smaller_average));

            if (mMinThrow > mNumberOfObjects)
                throw new IllegalArgumentException(getString(R.string.main_activity__invalid_min_throw_greater_average));
        }
        catch (NumberFormatException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.main_activity__invalid_input_value) + " " +
                            String.format(getString(R.string.main_activity__could_not_convert_to_int), e.getMessage()))
                    .setNeutralButton(getString(R.string.back), null);
            builder.create().show();
            return false;
        }
        catch (IllegalArgumentException e) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(getString(R.string.main_activity__invalid_input_value) + " " + e.getMessage())
                    .setNeutralButton(getString(R.string.back), null);
            builder.create().show();
            return false;
        }

        return true;
    }

    @Override
    protected void onStop() {
        super.onStop();

        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        updateFromTextEdits();

        editor.putInt(getString(R.string.main_activity__settings_number_of_objects), mNumberOfObjects);
        editor.putInt(getString(R.string.main_activity__settings_period_length), mPeriodLength);
        editor.putInt(getString(R.string.main_activity__settings_max_throw), mMaxThrow);
        editor.putInt(getString(R.string.main_activity__settings_min_throw), mMinThrow);
        editor.putInt(getString(R.string.main_activity__settings_number_of_jugglers), mNumberOfJugglers);
        editor.putInt(getString(R.string.main_activity__settings_max_results), mMaxResults);
        editor.putInt(getString(R.string.main_activity__settings_timeout), mTimeout);
        editor.putBoolean(getString(R.string.main_activity__settings_is_sync_pattern), mIsSyncPattern);
        editor.putBoolean(getString(R.string.main_activity__settings_is_random_generation_mode), mIsRandomGenerationMode);
        editor.putBoolean(getString(R.string.main_activity__settings_is_zips), mIsZips);
        editor.putBoolean(getString(R.string.main_activity__settings_is_zaps), mIsZaps);
        editor.putBoolean(getString(R.string.main_activity__settings_is_holds), mIsHolds);
        editor.putInt(getString(R.string.main_activity__settings_filter_spinner_position), mFilterSpinnerPosition);

        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            ObjectOutputStream so = new ObjectOutputStream(bo);
            so.writeObject(mFilterList);
            so.close();
            editor.putString(getString(R.string.main_activity__settings_filter_list), Base64.encodeToString(bo.toByteArray(), Base64.DEFAULT));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.main_activity__serialization_error_toast),
                    Toast.LENGTH_SHORT).show();
        }

        editor.commit();
    }


    public int getNumberOfSynchronousHands() {
        if (mIsSyncPattern) {
            return mNumberOfJugglers;
        }
        return 1;
    }


    public void addFilter(View view) {

        if (!updateFromTextEdits())
            return;

        if (mFilterTypeSpinner.getSelectedItemPosition() == PATTERN_FILTER_ITEM_NUMBER) {

            new PatternFilterDialog().show(getSupportFragmentManager(),
                    getString(R.string.pattern_filter__dialog_tag), mNumberOfJugglers);
        } else {
            new NumberFilterDialog().show(getSupportFragmentManager(),
                    getString(R.string.number_filter__dialog_tag),
                    mMinThrow, mMaxThrow, mPeriodLength, getNumberOfSynchronousHands());

        }
    }

    public void resetFilters(View view) {
        mFilterList.clear();
        updateAutoFilters();
    }


    public void onAddSiteswapFilter(Filter filter)
    {
        if (!mFilterList.contains(filter))
            mFilterList.add(filter);
        mFilterListAdapter.notifyDataSetChanged();
    }

    public void onRemoveSiteswapFilter(Filter filter)
    {
        // Remove all occurences of Filter
        while (mFilterList.remove(filter))
            ;
        mFilterListAdapter.notifyDataSetChanged();
    }

    public void onChangeSiteswapFilter(Filter oldFilter, Filter newFilter)
    {
        onRemoveSiteswapFilter(oldFilter);
        onAddSiteswapFilter(newFilter);
    }

    public void enterSiteswap(View button) {

        if (!updateFromTextEdits())
            return;

        new EnterSiteswapDialog().show(getSupportFragmentManager(),
                getString(R.string.enter_siteswap__dialog_tag),
                mNumberOfJugglers, mIsSyncPattern);
    }

    public void generateSiteswaps(View view) {

        if (!updateFromTextEdits())
            return;

        SiteswapGenerator siteswapGenerator = new SiteswapGenerator(mPeriodLength, mMaxThrow,
                mMinThrow, mNumberOfObjects, mNumberOfJugglers, mFilterList);
        siteswapGenerator.setMaxResults(mMaxResults);
        siteswapGenerator.setTimeoutSeconds(mTimeout);
        siteswapGenerator.setSyncPattern(mIsSyncPattern);
        siteswapGenerator.setRandomGeneration(mIsRandomGenerationMode);

        Intent intent = new Intent(this, ShowSiteswaps.class);
        intent.putExtra(getString(R.string.intent__siteswap_generator), siteswapGenerator);
        startActivity(intent);
    }

    public void onCheckboxClicked(View view) {

        boolean checked = ((CheckBox) view).isChecked();
        int oldNumberOfSynchronousHands = getNumberOfSynchronousHands();
        if (!updateFromTextEdits()) {
            ((CheckBox) view).setChecked(!checked);
            return;
        }

        switch (view.getId()) {
            case R.id.include_zips_checkbox:
                if (checked)
                    mFilterList.addZips(mNumberOfJugglers, getNumberOfSynchronousHands());
                else
                    mFilterList.removeZips(mNumberOfJugglers, getNumberOfSynchronousHands());
                break;
            case R.id.include_zaps_checkbox:
                if (checked)
                    mFilterList.addZaps(mNumberOfJugglers, getNumberOfSynchronousHands());
                else
                    mFilterList.removeZaps(mNumberOfJugglers, getNumberOfSynchronousHands());
                break;
            case R.id.include_holds_checkbox:
                if (checked)
                    mFilterList.addHolds(mNumberOfJugglers, getNumberOfSynchronousHands());
                else
                    mFilterList.removeHolds(mNumberOfJugglers, getNumberOfSynchronousHands());
                break;
            case R.id.sync_mode_checkbox:
                removeAutoFilters(oldNumberOfSynchronousHands);
                updateFiltersWithNumberOfSynchronousHands(getNumberOfSynchronousHands());
                addAutoFilters();
                break;
        }
        mFilterListAdapter.notifyDataSetChanged();

    }

    private boolean updateAutoFilters() {
        if (!updateFromTextEdits())
            return false;
        removeAutoFilters(getNumberOfSynchronousHands());
        addAutoFilters();
        mFilterListAdapter.notifyDataSetChanged();
        return true;
    }

    private void removeAutoFilters(int numberOfSynchronousHands) {
        mFilterList.removeDefaultFilters(mNumberOfJugglers, mMinThrow, numberOfSynchronousHands);
        mFilterList.addZips(mNumberOfJugglers, numberOfSynchronousHands);
        mFilterList.addZaps(mNumberOfJugglers, numberOfSynchronousHands);
        mFilterList.addHolds(mNumberOfJugglers, numberOfSynchronousHands);

    }

    private void addAutoFilters() {

        mFilterList.addDefaultFilters(mNumberOfJugglers, mMinThrow,
                getNumberOfSynchronousHands());
        if (!mIsZips)
            mFilterList.removeZips(mNumberOfJugglers, getNumberOfSynchronousHands());
        if (!mIsZaps)
            mFilterList.removeZaps(mNumberOfJugglers, getNumberOfSynchronousHands());
        if (!mIsHolds)
            mFilterList.removeHolds(mNumberOfJugglers, getNumberOfSynchronousHands());
    }

    private void updateFiltersWithNumberOfSynchronousHands(int numberOfSynchronousHands) {
        for (Filter filter : mFilterList) {
            if (filter instanceof NumberFilter) {
                ((NumberFilter) filter).setNumberOfSynchronousHands(numberOfSynchronousHands);
            }
        }
    }

    private void showNamedSiteswaps() {

        Intent intent = new Intent(this, NamedSiteswapActivity.class);
        startActivity(intent);
    }


    private void favorites() {
        Intent intent = new Intent(this, FavoritesActivity.class);
        startActivity(intent);

    }

    private void showAboutDialog()
    {

        try
        {

            PackageManager manager = getPackageManager();
            PackageInfo info = manager.getPackageInfo(getPackageName(), 0);
            String appVersion = info.versionName;

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            View view = getLayoutInflater().inflate(R.layout.layout_about_page, null);
            TextView appNameVersion = (TextView) view.findViewById(R.id.appNameVersion);
            appNameVersion.setText(getString(R.string.app_name) + " " + appVersion);

            builder.setView(view);
            builder.setNeutralButton(getString(R.string.back), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            });

            AlertDialog dialog = builder.create();
            dialog.show();

        } catch (Throwable t) {

            t.printStackTrace();
        }
    }
}
