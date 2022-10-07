/*
 * Copyright (C) 2022 Xilin Jia https://github.com/XilinJia
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
package org.gnucash.android.ui.settings.dialog

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import org.gnucash.android.R

/**
 * A fragment for adding an ownCloud account.
 * @author Xilin Jia <https://github.com/XilinJia> [Kotlin code created (Copyright (C) 2022)]
 */

class OwnCloudDialogFragment : DialogFragment() {
    /**
     * Dialog positive button. Ok to save and validate the data
     */
    private var mOkButton: Button? = null

    /**
     * Cancel button
     */
    private var mCancelButton: Button? = null

    /**
     * ownCloud vars
     */
    private var mOC_server: String? = null
    private var mOC_username: String? = null
    private var mOC_password: String? = null
    private var mOC_dir: String? = null
    private var mServer: EditText? = null
    private var mUsername: EditText? = null
    private var mPassword: EditText? = null
    private var mDir: EditText? = null
    private var mServerError: TextView? = null
    private var mUsernameError: TextView? = null
    private var mDirError: TextView? = null
    private lateinit var mPrefs: SharedPreferences
    private var mContext: Context? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
        mContext = activity
        mPrefs = mContext!!.getSharedPreferences(getString(R.string.owncloud_pref), Context.MODE_PRIVATE)
        mOC_server = mPrefs.getString(getString(R.string.key_owncloud_server), getString(R.string.owncloud_server))
        mOC_username = mPrefs.getString(getString(R.string.key_owncloud_username), null)
        mOC_password = mPrefs.getString(getString(R.string.key_owncloud_password), null)
        mOC_dir = mPrefs.getString(getString(R.string.key_owncloud_dir), getString(R.string.app_name))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.dialog_owncloud_account, container, false)
        mServer = view.findViewById<View>(R.id.owncloud_hostname) as EditText
        mUsername = view.findViewById<View>(R.id.owncloud_username) as EditText
        mPassword = view.findViewById<View>(R.id.owncloud_password) as EditText
        mDir = view.findViewById<View>(R.id.owncloud_dir) as EditText
        mServer!!.setText(mOC_server)
        mDir!!.setText(mOC_dir)
        mPassword!!.setText(mOC_password) // TODO: Remove - debugging only
        mUsername!!.setText(mOC_username)
        mServerError = view.findViewById<View>(R.id.owncloud_hostname_invalid) as TextView
        mUsernameError = view.findViewById<View>(R.id.owncloud_username_invalid) as TextView
        mDirError = view.findViewById<View>(R.id.owncloud_dir_invalid) as TextView
        mServerError!!.visibility = View.GONE
        mUsernameError!!.visibility = View.GONE
        mDirError!!.visibility = View.GONE
        mCancelButton = view.findViewById<View>(R.id.btn_cancel) as Button
        mOkButton = view.findViewById<View>(R.id.btn_save) as Button
        mOkButton!!.setText(R.string.btn_test)
        return view
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListeners()
    }

    private fun saveButton() {
        if (mDirError!!.text.toString() == getString(R.string.owncloud_dir_ok) && mUsernameError!!.text.toString() == getString(
                R.string.owncloud_user_ok
            ) && mServerError!!.text.toString() == getString(R.string.owncloud_server_ok)
        ) mOkButton!!.setText(R.string.btn_save) else mOkButton!!.setText(R.string.btn_test)
    }

    private fun save() {
        val edit = mPrefs.edit()
        edit.clear()
        edit.putString(getString(R.string.key_owncloud_server), mOC_server)
        edit.putString(getString(R.string.key_owncloud_username), mOC_username)
        edit.putString(getString(R.string.key_owncloud_password), mOC_password)
        edit.putString(getString(R.string.key_owncloud_dir), mOC_dir)
        edit.putBoolean(getString(R.string.owncloud_sync), true)
        edit.apply()
        if (ocCheckBox != null) ocCheckBox!!.isChecked = true
        dismiss()
    }

    private fun checkData() {}
    //  by XJ
    //    private void checkData0() {
    //        mServerError.setVisibility(View.GONE);
    //        mUsernameError.setVisibility(View.GONE);
    //        mDirError.setVisibility(View.GONE);
    //
    //        mOC_server = mServer.getText().toString().trim();
    //        mOC_username = mUsername.getText().toString().trim();
    //        mOC_password = mPassword.getText().toString().trim();
    //        mOC_dir = mDir.getText().toString().trim();
    //
    //        Uri serverUri = Uri.parse(mOC_server);
    //        OwnCloudClient mClient = OwnCloudClientFactory.createOwnCloudClient(serverUri, mContext, true);
    //        mClient.setCredentials(
    //                OwnCloudCredentialsFactory.newBasicCredentials(mOC_username, mOC_password)
    //        );
    //
    //        final Handler mHandler = new Handler();
    //
    //        OnRemoteOperationListener listener = new OnRemoteOperationListener() {
    //            @Override
    //            public void onRemoteOperationFinish(RemoteOperation caller, RemoteOperationResult result) {
    //                if (!result.isSuccess()) {
    //                    Log.e("OC", result.getLogMessage(), result.getException());
    //
    //                    if (caller instanceof GetRemoteStatusOperation) {
    //                        mServerError.setTextColor(ContextCompat.getColor(getContext(), R.color.debit_red));
    //                        mServerError.setText(getString(R.string.owncloud_server_invalid));
    //                        mServerError.setVisibility(View.VISIBLE);
    //
    //                    } else if (caller instanceof GetRemoteUserInfoOperation &&
    //                            mServerError.getText().toString().equals(getString(R.string.owncloud_server_ok))) {
    //                        mUsernameError.setTextColor(ContextCompat.getColor(getContext(), R.color.debit_red));
    //                        mUsernameError.setText(getString(R.string.owncloud_user_invalid));
    //                        mUsernameError.setVisibility(View.VISIBLE);
    //                    }
    //                } else {
    //                    if (caller instanceof GetRemoteStatusOperation) {
    //                        mServerError.setTextColor(ContextCompat.getColor(getContext(), R.color.theme_primary));
    //                        mServerError.setText(getString(R.string.owncloud_server_ok));
    //                        mServerError.setVisibility(View.VISIBLE);
    //                    } else if (caller instanceof GetRemoteUserInfoOperation) {
    //                        mUsernameError.setTextColor(ContextCompat.getColor(getContext(), R.color.theme_primary));
    //                        mUsernameError.setText(getString(R.string.owncloud_user_ok));
    //                        mUsernameError.setVisibility(View.VISIBLE);
    //                    }
    //                }
    //                saveButton();
    //            }
    //        };
    //
    //        GetRemoteStatusOperation g = new GetRemoteStatusOperation(mContext);
    //        g.execute(mClient, listener, mHandler);
    //
    //        GetRemoteUserInfoOperation gu = new GetRemoteUserInfoOperation();
    //        gu.execute(mClient, listener, mHandler);
    //
    //        if (FileUtils.isValidPath(mOC_dir, false)) {
    //            mDirError.setTextColor(ContextCompat.getColor(getContext(), R.color.theme_primary));
    //            mDirError.setText(getString(R.string.owncloud_dir_ok));
    //            mDirError.setVisibility(View.VISIBLE);
    //        } else {
    //            mDirError.setTextColor(ContextCompat.getColor(getContext(), R.color.debit_red));
    //            mDirError.setText(getString(R.string.owncloud_dir_invalid));
    //            mDirError.setVisibility(View.VISIBLE);
    //        }
    //        saveButton();
    //    }
    /**
     * Binds click listeners for the dialog buttons
     */
    private fun setListeners() {
        mCancelButton!!.setOnClickListener { dismiss() }
        mOkButton!!.setOnClickListener { // If data didn't change
            if (mOkButton!!.text.toString() == getString(R.string.btn_save) && mOC_server == mServer!!.text.toString()
                    .trim { it <= ' ' } && mOC_username == mUsername!!.text.toString()
                    .trim { it <= ' ' } && mOC_password == mPassword!!.text.toString()
                    .trim { it <= ' ' } && mOC_dir == mDir!!.text.toString().trim { it <= ' ' }
            ) save() else checkData()
        }
    }

    companion object {
        private var ocCheckBox: CheckBoxPreference? = null

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         * @return A new instance of fragment OwnCloudDialogFragment.
         */
        @JvmStatic
        fun newInstance(pref: Preference?): OwnCloudDialogFragment {
            val fragment = OwnCloudDialogFragment()
            ocCheckBox = if (pref == null) null else pref as CheckBoxPreference?
            return fragment
        }
    }
}