package com.simpleblocker.operations;

import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.text.TextUtils;
import android.util.Log;

import com.simpleblocker.SimpleBlockerApp;
import com.simpleblocker.data.DbHelper;
import com.simpleblocker.data.models.BlockedCallLog;
import com.simpleblocker.data.models.EmptyBlockedCallLog;
import com.simpleblocker.data.models.Phone;
import com.simpleblocker.prefs.Prefs;
import com.simpleblocker.utils.AppConfig;
import com.simpleblocker.utils.ExceptionUtils;
import com.simpleblocker.utils.TokenizerUtils;

public final class IncomingCallOperations extends Thread {

	private String incomingCallNumber;
	private String contactName;

	// ---------------- Getters & Setters ----------------------------------//

	public String getIncomingCallNumber() {
		return incomingCallNumber;
	}

	public String getContactName() {
		return contactName;
	}

	public void setContactName(String contactName) {
		this.contactName = contactName;
	}

	/**
	 * Constructor
	 * 
	 * @param incomingCallNumber
	 */
	public IncomingCallOperations(String incomingCallNumber) {
		this.incomingCallNumber = incomingCallNumber;
	}

	@Override
	public void run() {
		processIncomingCall();
	}

	/**
	 * Process incoming call
	 */
	public synchronized void processIncomingCall() {

		DbHelper dbHelper = new DbHelper(SimpleBlockerApp.getContext());
		try {

			Prefs prefs = new Prefs(SimpleBlockerApp.getContext());
			String phoneNumberWhithoutDashes = TokenizerUtils.tokenizerPhoneNumber(incomingCallNumber, null);
			if (TextUtils.isEmpty(phoneNumberWhithoutDashes)) phoneNumberWhithoutDashes = "";
			String contactName = "";
			if (!TextUtils.isEmpty(incomingCallNumber))
				contactName = getContactName(phoneNumberWhithoutDashes);

			// Put the mobile phone in silent mode (sound+vibration)
			// putRingerModeSilent();

			// End call using the ITelephony implementation
			// telephonyService.endCall();

			BlockedCallLog callLog = processBlockedActionType(dbHelper, prefs, phoneNumberWhithoutDashes, contactName);

			if (!callLog.isEmpty()) {
				dbHelper.insertBlockedCallLog(callLog);
			}
		} catch (Exception e) {
			Log.e(AppConfig.LOG_TAG, ExceptionUtils.getString(e));
		} finally {
			if (dbHelper != null) {
				dbHelper.cleanUp();
				dbHelper = null;
			}
		}
	}

	/**
	 * @param dbHelper
	 * @param incomingNumber
	 * @return
	 */
	private BlockedCallLog processBlockedActionType(final DbHelper dbHelper, final Prefs prefs, final String incomingNumber, final String contactName) {

		try {

			BlockedCallLog callLog = new EmptyBlockedCallLog();

			switch (AppConfig.BlockType.fromCode(prefs.getBlockType())) {
			case BLOCK_BLOCKED_CONTACTS:
				callLog = BlockOperations.blockBlockedContacts(dbHelper, prefs, incomingNumber, contactName);
				break;
			case BLOCK_UNKNOWN_AND_BLOCKED_CONTACTS:
				callLog = BlockOperations.blockUnknownAndBlockedContacts(dbHelper, prefs, incomingNumber, contactName);
				break;
			case BLOCK_UNKNOWN_CALLS:
				callLog = BlockOperations.blockUnknownCalls(dbHelper, prefs, incomingNumber, contactName);
				break;
			case BLOCK_ALL_CALL:
				callLog = BlockOperations.blockedAllCalls(dbHelper, prefs, incomingNumber, contactName);
				break;
			}
			return callLog;

		} catch (Exception e) {
			Log.e(AppConfig.LOG_TAG, ExceptionUtils.getString(e));
			return null;
		}
	}

	private String getContactName(final String incomingNumber) {
		String contactName = "";

		try {
			// Search in phone storage
			Uri personUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber));
			Cursor cur = SimpleBlockerApp.getContext().getContentResolver()
					.query(personUri, new String[] { ContactsContract.PhoneLookup.DISPLAY_NAME }, null, null, null);
			if (cur.moveToNext()) {
				int nameIdx = cur.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
				contactName = cur.getString(nameIdx);
			}
			cur.close();

			
			/*if (TextUtils.isEmpty(contactName)) {
				// Try searching in SIM
				String where = ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "' AND "
						+ ContactsContract.CommonDataKinds.Phone.NUMBER + "='" + incomingNumber + "'";
				Cursor phonesCursor = SimpleBlockerApp.getContext().getContentResolver()
						.query(ContactsContract.Data.CONTENT_URI, null, where, null, null);

				Log.d(AppConfig.LOG_TAG, "count: " + phonesCursor.getCount());

				// While the phonesCursor has content
				while (phonesCursor.moveToNext()) {
					String phoneNumber = phonesCursor.getString(phonesCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID));
				}
				// close the phones cursor
				phonesCursor.close();

			}
*/
		} catch (Exception e) {
			Log.e(AppConfig.LOG_TAG, ExceptionUtils.getString(e));
		}
		return contactName;
	}

}
