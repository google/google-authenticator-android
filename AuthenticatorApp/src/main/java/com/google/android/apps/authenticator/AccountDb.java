/*
 * Copyright 2010 Google Inc. All Rights Reserved.
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

package com.google.android.apps.authenticator;

import com.google.android.apps.authenticator.Base32String.DecodingException;
import com.google.android.apps.authenticator.PasscodeGenerator.Signer;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Process;
import android.util.Log;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * A database of email addresses and secret values
 *
 * @author sweis@google.com (Steve Weis)
 */
public class AccountDb {
  public static final Integer DEFAULT_HOTP_COUNTER = 0;

  public static final String GOOGLE_CORP_ACCOUNT_NAME = "Google Internal 2Factor";

  private static final String ID_COLUMN = "_id";
  private static final String EMAIL_COLUMN = "email";
  private static final String SECRET_COLUMN = "secret";
  private static final String COUNTER_COLUMN = "counter";
  private static final String TYPE_COLUMN = "type";
  // @VisibleForTesting
  static final String PROVIDER_COLUMN = "provider";
  // @VisibleForTesting
  static final String TABLE_NAME = "accounts";
  // @VisibleForTesting
  static final String PATH = "databases";

  private static final String TABLE_INFO_COLUMN_NAME_COLUMN = "name";

  private static final int PROVIDER_UNKNOWN = 0;
  private static final int PROVIDER_GOOGLE = 1;

  // @VisibleForTesting
  SQLiteDatabase mDatabase;

  private static final String LOCAL_TAG = "AccountDb";

  /**
   * Types of secret keys.
   */
  public enum OtpType {  // must be the same as in res/values/strings.xml:type
    TOTP (0),  // time based
    HOTP (1);  // counter based

    public final Integer value;  // value as stored in SQLite database
    OtpType(Integer value) {
      this.value = value;
    }

    public static OtpType getEnum(Integer i) {
      for (OtpType type : OtpType.values()) {
        if (type.value.equals(i)) {
          return type;
        }
      }

      return null;
    }

  }

  public AccountDb(Context context) {
    mDatabase = openDatabase(context);

    // Create the table if it doesn't exist
    mDatabase.execSQL(String.format(
        "CREATE TABLE IF NOT EXISTS %s" +
        " (%s INTEGER PRIMARY KEY, %s TEXT NOT NULL, %s TEXT NOT NULL, " +
        " %s INTEGER DEFAULT %s, %s INTEGER, %s INTEGER DEFAULT %s)",
        TABLE_NAME, ID_COLUMN, EMAIL_COLUMN, SECRET_COLUMN, COUNTER_COLUMN,
        DEFAULT_HOTP_COUNTER, TYPE_COLUMN,
        PROVIDER_COLUMN, PROVIDER_UNKNOWN));

    Collection<String> tableColumnNames = listTableColumnNamesLowerCase();
    if (!tableColumnNames.contains(PROVIDER_COLUMN.toLowerCase(Locale.US))) {
      // Migrate from old schema where the PROVIDER_COLUMN wasn't there
      mDatabase.execSQL(String.format(
          "ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT %s",
          TABLE_NAME, PROVIDER_COLUMN, PROVIDER_UNKNOWN));
    }
  }

  /*
   * Tries three times to open database before throwing AccountDbOpenException.
   */
  private SQLiteDatabase openDatabase(Context context) {
    for (int count = 0; true; count++) {
      try {
        return context.openOrCreateDatabase(PATH, Context.MODE_PRIVATE, null);
      } catch (SQLiteException e) {
        if (count < 2) {
          continue;
        } else {
          throw new AccountDbOpenException("Failed to open AccountDb database in three tries.\n"
              + getAccountDbOpenFailedErrorString(context), e);
        }
      }
    }
  }

  private String getAccountDbOpenFailedErrorString(Context context) {
    String dataPackageDir = context.getApplicationInfo().dataDir;
    String databaseDirPathname = context.getDatabasePath(PATH).getParent();
    String databasePathname = context.getDatabasePath(PATH).getAbsolutePath();
    String[] dirsToStat = new String[] {dataPackageDir, databaseDirPathname, databasePathname};
    StringBuilder error = new StringBuilder();
    int myUid = Process.myUid();
    for (String directory : dirsToStat) {
      try {
        FileUtilities.StatStruct stat = FileUtilities.getStat(directory);
        String ownerUidName = null;
        try {
          if (stat.uid == 0) {
            ownerUidName = "root";
          } else {
            PackageManager packageManager = context.getPackageManager();
            ownerUidName = (packageManager != null) ? packageManager.getNameForUid(stat.uid) : null;
          }
        } catch (Exception e) {
          ownerUidName = e.toString();
        }
        error.append(directory + " directory stat (my UID: " + myUid);
        if (ownerUidName == null) {
          error.append("): ");
        } else {
          error.append(", dir owner UID name: " + ownerUidName + "): ");
        }
        error.append(stat.toString() + "\n");
      } catch (IOException e) {
        error.append(directory + " directory stat threw an exception: " + e + "\n");
      }
    }
    return error.toString();
  }

  /**
   * Closes this database and releases any system resources held.
   */
  public void close() {
    mDatabase.close();
  }

  /**
   * Lists the names of all the columns in the specified table.
   */
  // @VisibleForTesting
  static Collection<String> listTableColumnNamesLowerCase(
      SQLiteDatabase database, String tableName) {
    Cursor cursor =
        database.rawQuery(String.format("PRAGMA table_info(%s)", tableName), new String[0]);
    Collection<String> result = new ArrayList<String>();
    try {
      if (cursor != null) {
        int nameColumnIndex = cursor.getColumnIndexOrThrow(TABLE_INFO_COLUMN_NAME_COLUMN);
        while (cursor.moveToNext()) {
          result.add(cursor.getString(nameColumnIndex).toLowerCase(Locale.US));
        }
      }
      return result;
    } finally {
      tryCloseCursor(cursor);
    }
  }

  /**
   * Lists the names of all the columns in the accounts table.
   */
  private Collection<String> listTableColumnNamesLowerCase() {
    return listTableColumnNamesLowerCase(mDatabase, TABLE_NAME);
  }

  /*
   * deleteAllData() will remove all rows. Useful for testing.
   */
  public boolean deleteAllData() {
    mDatabase.delete(AccountDb.TABLE_NAME, null, null);
    return true;
  }

  public boolean nameExists(String email) {
    Cursor cursor = getAccount(email);
    try {
      return !cursorIsEmpty(cursor);
    } finally {
      tryCloseCursor(cursor);
    }
  }

  public String getSecret(String email) {
    Cursor cursor = getAccount(email);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        return cursor.getString(cursor.getColumnIndex(SECRET_COLUMN));
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return null;
  }

  static Signer getSigningOracle(String secret) {
    try {
      byte[] keyBytes = decodeKey(secret);
      final Mac mac = Mac.getInstance("HMACSHA1");
      mac.init(new SecretKeySpec(keyBytes, ""));

      // Create a signer object out of the standard Java MAC implementation.
      return new Signer() {
        @Override
        public byte[] sign(byte[] data) {
          return mac.doFinal(data);
        }
      };
    } catch (DecodingException error) {
      Log.e(LOCAL_TAG, error.getMessage());
    } catch (NoSuchAlgorithmException error) {
      Log.e(LOCAL_TAG, error.getMessage());
    } catch (InvalidKeyException error) {
      Log.e(LOCAL_TAG, error.getMessage());
    }

    return null;
  }

  private static byte[] decodeKey(String secret) throws DecodingException {
    return Base32String.decode(secret);
  }

  public Integer getCounter(String email) {
    Cursor cursor = getAccount(email);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        return cursor.getInt(cursor.getColumnIndex(COUNTER_COLUMN));
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return null;
  }

  void incrementCounter(String email) {
    ContentValues values = new ContentValues();
    values.put(EMAIL_COLUMN, email);
    Integer counter = getCounter(email);
    values.put(COUNTER_COLUMN, counter + 1);
    mDatabase.update(TABLE_NAME, values, whereClause(email), null);
  }

  public OtpType getType(String email) {
    Cursor cursor = getAccount(email);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        Integer value = cursor.getInt(cursor.getColumnIndex(TYPE_COLUMN));
        return OtpType.getEnum(value);
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return null;
  }

  void setType(String email, OtpType type) {
    ContentValues values = new ContentValues();
    values.put(EMAIL_COLUMN, email);
    values.put(TYPE_COLUMN, type.value);
    mDatabase.update(TABLE_NAME, values, whereClause(email), null);
  }

  public boolean isGoogleAccount(String email) {
    Cursor cursor = getAccount(email);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        if (cursor.getInt(cursor.getColumnIndex(PROVIDER_COLUMN)) == PROVIDER_GOOGLE) {
          // The account is marked as source: Google
          return true;
        }
        // The account is from an unknown source. Could be a Google account added by scanning
        // a QR code or by manually entering a key
        String emailLowerCase = email.toLowerCase(Locale.US);
        return(emailLowerCase.endsWith("@gmail.com"))
            || (emailLowerCase.endsWith("@google.com"))
            || (email.equals(GOOGLE_CORP_ACCOUNT_NAME));
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return false;
  }

  /**
   * Finds the Google corp account in this database.
   *
   * @return the name of the account if it is present or {@code null} if the account does not exist.
   */
  public String findGoogleCorpAccount() {
    return nameExists(GOOGLE_CORP_ACCOUNT_NAME) ? GOOGLE_CORP_ACCOUNT_NAME : null;
  }

  private static String whereClause(String email) {
    return EMAIL_COLUMN + " = " + DatabaseUtils.sqlEscapeString(email);
  }

  public void delete(String email) {
    mDatabase.delete(TABLE_NAME, whereClause(email), null);
  }

  /**
   * Save key to database, creating a new user entry if necessary.
   * @param email the user email address. When editing, the new user email.
   * @param secret the secret key.
   * @param oldEmail If editing, the original user email, otherwise null.
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   */
  public void update(String email, String secret, String oldEmail,
      OtpType type, Integer counter) {
    update(email, secret, oldEmail, type, counter, null);
  }

  /**
   * Save key to database, creating a new user entry if necessary.
   * @param email the user email address. When editing, the new user email.
   * @param secret the secret key.
   * @param oldEmail If editing, the original user email, otherwise null.
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   * @param googleAccount whether the key is for a Google account or {@code null} to preserve
   *        the previous value (or use a default if adding a key).
   */
  public void update(String email, String secret, String oldEmail,
      OtpType type, Integer counter, Boolean googleAccount) {
    ContentValues values = new ContentValues();
    values.put(EMAIL_COLUMN, email);
    values.put(SECRET_COLUMN, secret);
    values.put(TYPE_COLUMN, type.ordinal());
    values.put(COUNTER_COLUMN, counter);
    if (googleAccount != null) {
      values.put(
          PROVIDER_COLUMN,
          (googleAccount.booleanValue()) ? PROVIDER_GOOGLE : PROVIDER_UNKNOWN);
    }
    int updated = mDatabase.update(TABLE_NAME, values,
                                  whereClause(oldEmail), null);
    if (updated == 0) {
      mDatabase.insert(TABLE_NAME, null, values);
    }
  }

  private Cursor getNames() {
    return mDatabase.query(TABLE_NAME, null, null, null, null, null, null, null);
  }

  private Cursor getAccount(String email) {
    return mDatabase.query(TABLE_NAME, null, EMAIL_COLUMN + "= ?",
        new String[] {email}, null, null, null);
  }

  /**
   * Returns true if the cursor is null, or contains no rows.
   */
  private static boolean cursorIsEmpty(Cursor c) {
    return c == null || c.getCount() == 0;
  }

  /**
   * Closes the cursor if it is not null and not closed.
   */
  private static void tryCloseCursor(Cursor c) {
    if (c != null && !c.isClosed()) {
      c.close();
    }
  }

  /**
   * Get list of all account names.
   * @param result Collection of strings-- account names are appended, without
   *               clearing this collection on entry.
   * @return Number of accounts added to the output parameter.
   */
  public int getNames(Collection<String> result) {
    Cursor cursor = getNames();

    try {
      if (cursorIsEmpty(cursor))
        return 0;

      int nameCount = cursor.getCount();
      int index = cursor.getColumnIndex(AccountDb.EMAIL_COLUMN);

      for (int i = 0; i < nameCount; ++i) {
        cursor.moveToPosition(i);
        String username = cursor.getString(index);
        result.add(username);
      }

      return nameCount;
    } finally {
      tryCloseCursor(cursor);
    }
  }

  private static class AccountDbOpenException extends RuntimeException {
    public AccountDbOpenException(String message, Exception e) {
      super(message, e);
    }
  }

}
