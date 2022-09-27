/*
 * Copyright (c) 2012-2013 Ngewi Fet <ngewif@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gnucash.android.ui.export

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.*
import android.widget.AdapterView.OnItemSelectedListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import butterknife.BindView
import butterknife.ButterKnife
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment
import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrence
import com.codetroopers.betterpickers.recurrencepicker.EventRecurrenceFormatter
import com.codetroopers.betterpickers.recurrencepicker.RecurrencePickerDialogFragment.OnRecurrenceSetListener
import com.dropbox.core.android.Auth
import org.gnucash.android.R
import org.gnucash.android.app.GnuCashApplication
import org.gnucash.android.db.adapter.BooksDbAdapter
import org.gnucash.android.db.adapter.DatabaseAdapter
import org.gnucash.android.db.adapter.ScheduledActionDbAdapter
import org.gnucash.android.export.DropboxHelper.hasToken
import org.gnucash.android.export.DropboxHelper.retrieveAndSaveToken
import org.gnucash.android.export.ExportAsyncTask
import org.gnucash.android.export.ExportFormat
import org.gnucash.android.export.ExportParams
import org.gnucash.android.export.ExportParams.ExportTarget
import org.gnucash.android.export.Exporter.Companion.buildExportFilename
import org.gnucash.android.model.BaseModel.Companion.generateUID
import org.gnucash.android.model.ScheduledAction
import org.gnucash.android.ui.common.UxArgument
import org.gnucash.android.ui.settings.BackupPreferenceFragment
import org.gnucash.android.ui.settings.dialog.OwnCloudDialogFragment
import org.gnucash.android.ui.transaction.TransactionFormFragment
import org.gnucash.android.ui.util.RecurrenceParser
import org.gnucash.android.ui.util.RecurrenceViewClickListener
import org.gnucash.android.util.PreferencesHelper
import org.gnucash.android.util.TimestampHelper
import java.sql.Timestamp
import java.text.ParseException
import java.util.*

/**
 * Dialog fragment for exporting accounts and transactions in various formats
 *
 * The dialog is used for collecting information on the export options and then passing them
 * to the [org.gnucash.android.export.Exporter] responsible for exporting
 * @author Ngewi Fet <ngewif></ngewif>@gmail.com>
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */
class ExportFormFragment() : Fragment(), OnRecurrenceSetListener, CalendarDatePickerDialogFragment.OnDateSetListener,
    RadialTimePickerDialogFragment.OnTimeSetListener {
    /**
     * Spinner for selecting destination for the exported file.
     * The destination could either be SD card, or another application which
     * accepts files, like Google Drive.
     */
    @JvmField
	@BindView(R.id.spinner_export_destination)
    var mDestinationSpinner: Spinner? = null

    /**
     * Checkbox for deleting all transactions after exporting them
     */
    @JvmField
	@BindView(R.id.checkbox_post_export_delete)
    var mDeleteAllCheckBox: CheckBox? = null

    /**
     * Text view for showing warnings based on chosen export format
     */
    @JvmField
	@BindView(R.id.export_warning)
    var mExportWarningTextView: TextView? = null

    @JvmField
	@BindView(R.id.target_uri)
    var mTargetUriTextView: TextView? = null

    /**
     * Recurrence text view
     */
    @JvmField
	@BindView(R.id.input_recurrence)
    var mRecurrenceTextView: TextView? = null

    /**
     * Text view displaying start date to export from
     */
    @JvmField
	@BindView(R.id.export_start_date)
    var mExportStartDate: TextView? = null

    @JvmField
	@BindView(R.id.export_start_time)
    var mExportStartTime: TextView? = null

    /**
     * Switch toggling whether to export all transactions or not
     */
    @JvmField
	@BindView(R.id.switch_export_all)
    var mExportAllSwitch: SwitchCompat? = null

    @JvmField
	@BindView(R.id.export_date_layout)
    var mExportDateLayout: LinearLayout? = null

    @JvmField
	@BindView(R.id.radio_ofx_format)
    var mOfxRadioButton: RadioButton? = null

    @JvmField
	@BindView(R.id.radio_qif_format)
    var mQifRadioButton: RadioButton? = null

    @JvmField
	@BindView(R.id.radio_xml_format)
    var mXmlRadioButton: RadioButton? = null

    @JvmField
	@BindView(R.id.radio_csv_transactions_format)
    var mCsvTransactionsRadioButton: RadioButton? = null

    @JvmField
	@BindView(R.id.radio_separator_comma_format)
    var mSeparatorCommaButton: RadioButton? = null

    @JvmField
	@BindView(R.id.radio_separator_colon_format)
    var mSeparatorColonButton: RadioButton? = null

    @JvmField
	@BindView(R.id.radio_separator_semicolon_format)
    var mSeparatorSemicolonButton: RadioButton? = null

    @JvmField
	@BindView(R.id.layout_csv_options)
    var mCsvOptionsLayout: LinearLayout? = null

    @JvmField
	@BindView(R.id.recurrence_options)
    var mRecurrenceOptionsView: View? = null

    /**
     * Event recurrence options
     */
    private val mEventRecurrence = EventRecurrence()

    /**
     * Recurrence rule
     */
    private var mRecurrenceRule: String? = null
    private val mExportStartCalendar = Calendar.getInstance()

    /**
     * Export format
     */
    private var mExportFormat = ExportFormat.QIF
    private var mExportTarget = ExportTarget.SD_CARD

    /**
     * The Uri target for the export
     */
    private var mExportUri: Uri? = null
    private var mExportCsvSeparator = ','

    /**
     * Flag to determine if export has been started.
     * Used to continue export after user has picked a destination file
     */
    private var mExportStarted = false
    private fun onRadioButtonClicked(view: View) {
        when (view.id) {
            R.id.radio_ofx_format -> {
                mExportFormat = ExportFormat.OFX
                if (GnuCashApplication.isDoubleEntryEnabled) {
                    mExportWarningTextView!!.text = activity!!.getString(R.string.export_warning_ofx)
                    mExportWarningTextView!!.visibility = View.VISIBLE
                } else {
                    mExportWarningTextView!!.visibility = View.GONE
                }
                OptionsViewAnimationUtils.expand(mExportDateLayout)
                OptionsViewAnimationUtils.collapse(mCsvOptionsLayout)
            }

            R.id.radio_qif_format -> {
                mExportFormat = ExportFormat.QIF
                //TODO: Also check that there exist transactions with multiple currencies before displaying warning
                if (GnuCashApplication.isDoubleEntryEnabled) {
                    mExportWarningTextView!!.text = activity!!.getString(R.string.export_warning_qif)
                    mExportWarningTextView!!.visibility = View.VISIBLE
                } else {
                    mExportWarningTextView!!.visibility = View.GONE
                }
                OptionsViewAnimationUtils.expand(mExportDateLayout)
                OptionsViewAnimationUtils.collapse(mCsvOptionsLayout)
            }

            R.id.radio_xml_format -> {
                mExportFormat = ExportFormat.XML
                mExportWarningTextView!!.setText(R.string.export_warning_xml)
                OptionsViewAnimationUtils.collapse(mExportDateLayout)
                OptionsViewAnimationUtils.collapse(mCsvOptionsLayout)
            }

            R.id.radio_csv_transactions_format -> {
                mExportFormat = ExportFormat.CSVT
                mExportWarningTextView!!.setText(R.string.export_notice_csv)
                OptionsViewAnimationUtils.expand(mExportDateLayout)
                OptionsViewAnimationUtils.expand(mCsvOptionsLayout)
            }

            R.id.radio_separator_comma_format -> mExportCsvSeparator = ','
            R.id.radio_separator_colon_format -> mExportCsvSeparator = ':'
            R.id.radio_separator_semicolon_format -> mExportCsvSeparator = ';'
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_export_form, container, false)
        ButterKnife.bind(this, view)
        bindViewListeners()
        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.default_save_actions, menu)
        val menuItem = menu.findItem(R.id.menu_save)
        menuItem.setTitle(R.string.btn_export)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> {
                startExport()
                return true
            }

            android.R.id.home -> {
                activity!!.finish()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val supportActionBar = (activity as AppCompatActivity?)!!.supportActionBar
        assert(supportActionBar != null)
        supportActionBar!!.setTitle(R.string.title_export_dialog)
        setHasOptionsMenu(true)
    }

    override fun onResume() {
        super.onResume()
        retrieveAndSaveToken()
    }

    override fun onPause() {
        super.onPause()
        // When the user try to export sharing to 3rd party service like DropBox
        // then pausing all activities. That cause passcode screen appearing happened.
        // We use a disposable flag to skip this unnecessary passcode screen.
        val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
        prefs.edit().putBoolean(UxArgument.SKIP_PASSCODE_SCREEN, true).apply()
    }

    /**
     * Starts the export of transactions with the specified parameters
     */
    private fun startExport() {
        if (mExportTarget === ExportTarget.URI && mExportUri == null) {
            mExportStarted = true
            selectExportFile()
            return
        }
        val exportParameters = ExportParams(mExportFormat)
        if (mExportAllSwitch!!.isChecked) {
            exportParameters.exportStartTime = TimestampHelper.timestampFromEpochZero
        } else {
            exportParameters.exportStartTime = Timestamp(mExportStartCalendar.timeInMillis)
        }
        exportParameters.exportTarget = mExportTarget
        exportParameters.exportLocation = if (mExportUri != null) mExportUri.toString() else null
        exportParameters.setDeleteTransactionsAfterExport(mDeleteAllCheckBox!!.isChecked)
        exportParameters.csvSeparator = mExportCsvSeparator
        Log.i(TAG, "Commencing async export of transactions")
        ExportAsyncTask((activity)!!, GnuCashApplication.activeDb).execute(exportParameters)
        if (mRecurrenceRule != null) {
            val scheduledAction = ScheduledAction(ScheduledAction.ActionType.BACKUP)
            scheduledAction.setMRecurrence(RecurrenceParser.parse(mEventRecurrence)!!)
            scheduledAction.mTag = exportParameters.toCsv()
            scheduledAction.setMActionUID(generateUID())
            ScheduledActionDbAdapter.instance.addRecord(scheduledAction, DatabaseAdapter.UpdateMethod.insert)
        }
        val position = mDestinationSpinner!!.selectedItemPosition
        PreferenceManager.getDefaultSharedPreferences(activity)
            .edit().putInt(getString(R.string.key_last_export_destination), position)
            .apply()

        // finish the activity will cause the progress dialog to be leaked
        // which would throw an exception
        //getActivity().finish();
    }

    /**
     * Bind views to actions when initializing the export form
     */
    private fun bindViewListeners() {
        // export destination bindings
        val adapter = ArrayAdapter.createFromResource(
            (activity)!!,
            R.array.export_destinations, android.R.layout.simple_spinner_item
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mDestinationSpinner!!.adapter = adapter
        mDestinationSpinner!!.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                if (view == null) //the item selection is fired twice by the Android framework. Ignore the first one
                    return
                when (position) {
                    0 -> {
                        mExportTarget = ExportTarget.URI
                        mRecurrenceOptionsView!!.visibility = View.VISIBLE
                        if (mExportUri != null) setExportUriText(mExportUri.toString())
                    }

                    1 -> {
                        setExportUriText(getString(R.string.label_dropbox_export_destination))
                        mRecurrenceOptionsView!!.visibility = View.VISIBLE
                        mExportTarget = ExportTarget.DROPBOX
                        val dropboxAppKey: String = getString(R.string.dropbox_app_key, BackupPreferenceFragment.DROPBOX_APP_KEY)
                        val dropboxAppSecret: String = getString(R.string.dropbox_app_secret, BackupPreferenceFragment.DROPBOX_APP_SECRET)
                        if (!hasToken()) {
                            Auth.startOAuth2Authentication(activity, dropboxAppKey)
                        }
                    }

                    2 -> {
                        setExportUriText(null)
                        mRecurrenceOptionsView!!.visibility = View.VISIBLE
                        mExportTarget = ExportTarget.OWNCLOUD
                        if (!(PreferenceManager.getDefaultSharedPreferences(activity)
                                .getBoolean(getString(R.string.key_owncloud_sync), false))
                        ) {
                            val ocDialog: OwnCloudDialogFragment = OwnCloudDialogFragment.newInstance(null)
                            ocDialog.show(activity!!.supportFragmentManager, "ownCloud dialog")
                        }
                    }

                    3 -> {
                        setExportUriText(getString(R.string.label_select_destination_after_export))
                        mExportTarget = ExportTarget.SHARING
                        mRecurrenceOptionsView!!.visibility = View.GONE
                    }

                    else -> mExportTarget = ExportTarget.SD_CARD
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                //nothing to see here, move along
            }
        }
        val position = PreferenceManager.getDefaultSharedPreferences(activity)
            .getInt(getString(R.string.key_last_export_destination), 0)
        mDestinationSpinner!!.setSelection(position)

        //**************** export start time bindings ******************
        val timestamp = PreferencesHelper.lastExportTime
        mExportStartCalendar.timeInMillis = timestamp!!.time
        val date = Date(timestamp.time)
        mExportStartDate!!.text = TransactionFormFragment.DATE_FORMATTER.format(date)
        mExportStartTime!!.text = TransactionFormFragment.TIME_FORMATTER.format(date)
        mExportStartDate!!.setOnClickListener(View.OnClickListener {
            var dateMillis: Long = 0
            try {
                val date = TransactionFormFragment.DATE_FORMATTER.parse(mExportStartDate!!.text.toString())
                dateMillis = date.time
            } catch (e: ParseException) {
                Log.e(tag, "Error converting input time to Date object")
            }
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = dateMillis
            val year = calendar[Calendar.YEAR]
            val monthOfYear = calendar[Calendar.MONTH]
            val dayOfMonth = calendar[Calendar.DAY_OF_MONTH]
            val datePickerDialog = CalendarDatePickerDialogFragment()
            datePickerDialog.setOnDateSetListener(this@ExportFormFragment)
            datePickerDialog.setPreselectedDate(year, monthOfYear, dayOfMonth)
            datePickerDialog.show((fragmentManager)!!, "date_picker_fragment")
        })
        mExportStartTime!!.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View) {
                var timeMillis: Long = 0
                try {
                    val date = TransactionFormFragment.TIME_FORMATTER.parse(mExportStartTime!!.text.toString())
                    timeMillis = date.time
                } catch (e: ParseException) {
                    Log.e(tag, "Error converting input time to Date object")
                }
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = timeMillis
                val timePickerDialog = RadialTimePickerDialogFragment()
                timePickerDialog.setOnTimeSetListener(this@ExportFormFragment)
                timePickerDialog.setStartTime(
                    calendar[Calendar.HOUR_OF_DAY],
                    calendar[Calendar.MINUTE]
                )
                timePickerDialog.show((fragmentManager)!!, "time_picker_dialog_fragment")
            }
        })
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity)
        mExportAllSwitch!!.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
                mExportStartDate!!.isEnabled = !isChecked
                mExportStartTime!!.isEnabled = !isChecked
                val color = if (isChecked) android.R.color.darker_gray else android.R.color.black
                mExportStartDate!!.setTextColor(ContextCompat.getColor((context)!!, color))
                mExportStartTime!!.setTextColor(ContextCompat.getColor((context)!!, color))
            }
        })
        mExportAllSwitch!!.isChecked = sharedPrefs.getBoolean(getString(R.string.key_export_all_transactions), false)
        mDeleteAllCheckBox!!.isChecked =
            sharedPrefs.getBoolean(getString(R.string.key_delete_transactions_after_export), false)
        mRecurrenceTextView!!.setOnClickListener(
            RecurrenceViewClickListener(
                activity as AppCompatActivity,
                mRecurrenceRule!!,
                this
            )
        )

        //this part (setting the export format) must come after the recurrence view bindings above
        val defaultExportFormat =
            sharedPrefs.getString(getString(R.string.key_default_export_format), ExportFormat.CSVT.name)
        mExportFormat = ExportFormat.valueOf((defaultExportFormat)!!)
        val radioClickListener: View.OnClickListener = object : View.OnClickListener {
            override fun onClick(view: View) {
                onRadioButtonClicked(view)
            }
        }
        val v = view
        assert(v != null)
        mOfxRadioButton!!.setOnClickListener(radioClickListener)
        mQifRadioButton!!.setOnClickListener(radioClickListener)
        mXmlRadioButton!!.setOnClickListener(radioClickListener)
        mCsvTransactionsRadioButton!!.setOnClickListener(radioClickListener)
        mSeparatorCommaButton!!.setOnClickListener(radioClickListener)
        mSeparatorColonButton!!.setOnClickListener(radioClickListener)
        mSeparatorSemicolonButton!!.setOnClickListener(radioClickListener)
        val defaultFormat = ExportFormat.valueOf(
            defaultExportFormat.uppercase(Locale.getDefault())
        )
        when (defaultFormat) {
            ExportFormat.QIF -> mQifRadioButton!!.performClick()
            ExportFormat.OFX -> mOfxRadioButton!!.performClick()
            ExportFormat.XML -> mXmlRadioButton!!.performClick()
            ExportFormat.CSVT -> mCsvTransactionsRadioButton!!.performClick()
            else -> {}
        }
        if (GnuCashApplication.isDoubleEntryEnabled) {
            mOfxRadioButton!!.visibility = View.GONE
        } else {
            mXmlRadioButton!!.visibility = View.GONE
        }
    }

    /**
     * Display the file path of the file where the export will be saved
     * @param filepath Path to export file. If `null`, the view will be hidden and nothing displayed
     */
    private fun setExportUriText(filepath: String?) {
        if (filepath == null) {
            mTargetUriTextView!!.visibility = View.GONE
            mTargetUriTextView!!.text = ""
        } else {
            mTargetUriTextView!!.text = filepath
            mTargetUriTextView!!.visibility = View.VISIBLE
        }
    }

    /**
     * Open a chooser for user to pick a file to export to
     */
    private fun selectExportFile() {
        val createIntent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        createIntent.setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
        val bookName = BooksDbAdapter.instance.activeBookDisplayName
        val filename = buildExportFilename(mExportFormat, bookName)
        createIntent.putExtra(Intent.EXTRA_TITLE, filename)
        startActivityForResult(createIntent, REQUEST_EXPORT_FILE)
    }

    override fun onRecurrenceSet(rrule: String) {
        mRecurrenceRule = rrule
        var repeatString: String? = getString(R.string.label_tap_to_create_schedule)
        if (mRecurrenceRule != null) {
            mEventRecurrence.parse(mRecurrenceRule)
            repeatString = EventRecurrenceFormatter.getRepeatString(
                activity, resources,
                mEventRecurrence, true
            )
        }
        mRecurrenceTextView!!.text = repeatString
    }

    /**
     * Callback for when the activity chooser dialog is completed
     */
    @SuppressLint("WrongConstant")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            BackupPreferenceFragment.REQUEST_RESOLVE_CONNECTION -> if (resultCode == Activity.RESULT_OK) {
                BackupPreferenceFragment.mGoogleApiClient.connect()
            }

            REQUEST_EXPORT_FILE -> if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    mExportUri = data.data
                }
                val takeFlags = (data!!.flags
                        and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
                activity!!.contentResolver.takePersistableUriPermission((mExportUri)!!, takeFlags)
                mTargetUriTextView!!.text = mExportUri.toString()
                if (mExportStarted) startExport()
            }
        }
    }

    override fun onDateSet(dialog: CalendarDatePickerDialogFragment, year: Int, monthOfYear: Int, dayOfMonth: Int) {
        val cal: Calendar = GregorianCalendar(year, monthOfYear, dayOfMonth)
        mExportStartDate!!.text = TransactionFormFragment.DATE_FORMATTER.format(cal.time)
        mExportStartCalendar[Calendar.YEAR] = year
        mExportStartCalendar[Calendar.MONTH] = monthOfYear
        mExportStartCalendar[Calendar.DAY_OF_MONTH] = dayOfMonth
    }

    override fun onTimeSet(dialog: RadialTimePickerDialogFragment, hourOfDay: Int, minute: Int) {
        val cal: Calendar = GregorianCalendar(0, 0, 0, hourOfDay, minute)
        mExportStartTime!!.text = TransactionFormFragment.TIME_FORMATTER.format(cal.time)
        mExportStartCalendar[Calendar.HOUR_OF_DAY] = hourOfDay
        mExportStartCalendar[Calendar.MINUTE] = minute
    }

    companion object {
        /**
         * Request code for intent to pick export file destination
         */
        private val REQUEST_EXPORT_FILE = 0x14

        /**
         * Tag for logging
         */
        private val TAG = "ExportFormFragment"
    }
} // Gotten from: https://stackoverflow.com/a/31720191

internal object OptionsViewAnimationUtils {
    fun expand(v: View?) {
        v!!.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val targetHeight = v.measuredHeight
        v.layoutParams.height = 0
        v.visibility = View.VISIBLE
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                v.layoutParams.height =
                    if (interpolatedTime == 1f) ViewGroup.LayoutParams.WRAP_CONTENT else (targetHeight * interpolatedTime).toInt()
                v.requestLayout()
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }
        a.duration = (3 * targetHeight / v.context.resources.displayMetrics.density).toInt().toLong()
        v.startAnimation(a)
    }

    fun collapse(v: View?) {
        val initialHeight = v!!.measuredHeight
        val a: Animation = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                if (interpolatedTime == 1f) {
                    v.visibility = View.GONE
                } else {
                    v.layoutParams.height = initialHeight - (initialHeight * interpolatedTime).toInt()
                    v.requestLayout()
                }
            }

            override fun willChangeBounds(): Boolean {
                return true
            }
        }
        a.duration = (3 * initialHeight / v.context.resources.displayMetrics.density).toInt().toLong()
        v.startAnimation(a)
    }
}