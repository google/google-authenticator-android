/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.apps.authenticator.otp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import com.google.android.apps.authenticator.otp.PasscodeGenerator.Signer;
import com.google.android.apps.authenticator.util.Base32String;
import com.google.android.apps.authenticator.util.Base32String.DecodingException;
import com.google.android.apps.authenticator.util.FileUtilities;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** A database of account names and secret values. */
public class AccountDb {
  public static final Integer DEFAULT_HOTP_COUNTER = 0;

  public static final String GOOGLE_CORP_ACCOUNT_NAME = "Google Internal 2Factor";

  public static final String GOOGLE_ISSUER_NAME = "Google";

  /**
   * A list of issuers that will automatically be added to older accounts that have a matching
   * prefix of ("issuer:") during the first upgrade to an issuer-supporting database.
   */
  @VisibleForTesting
  static final String[] AUTO_UPGRADE_ISSUERS = { GOOGLE_ISSUER_NAME, "Dropbox" };

  /**
   * Maximum number of accounts with the same {@code name} but no {@code issuer} specified.
   */
  @VisibleForTesting
  static final int MAX_DUPLICATE_NAMES = 20;

  @VisibleForTesting
  static final String ID_COLUMN = "_id";

  /**
   * Note: the value is "email" for historical reasons
   */
  @VisibleForTesting
  static final String NAME_COLUMN = "email";
  @VisibleForTesting
  static final String SECRET_COLUMN = "secret";
  @VisibleForTesting
  static final String COUNTER_COLUMN = "counter";
  @VisibleForTesting
  static final String TYPE_COLUMN = "type";
  @VisibleForTesting
  static final String PROVIDER_COLUMN = "provider";
  @VisibleForTesting
  static final String ISSUER_COLUMN = "issuer";
  /**
   * This column is used to preserve the account name as it appeared in the QR code that was used
   * to add the account. Thus, even if the account is later renamed it can be matched with the
   * corresponding GLS account name if need be.
   */
  @VisibleForTesting
  static final String ORIGINAL_NAME_COLUMN = "original_name";
  @VisibleForTesting
  static final String TABLE_NAME = "accounts";

  private static final String TABLE_INFO_COLUMN_NAME_COLUMN = "name";

  @VisibleForTesting
  static final int PROVIDER_UNKNOWN = 0;
  @VisibleForTesting
  static final int PROVIDER_GOOGLE = 1;

  private static final int INVALID_ID = -1;

  // TODO: Consider making the general DB more sane by using SQLiteOpenHelper and/or
  // android.arch.persistence.room.Query. Or store everything in Keystore.
  @VisibleForTesting
  SQLiteDatabase mDatabase;

  private static final String LOCAL_TAG = "GAuthenticator.AcctDb";

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

  /**
   * An immutable object that encapsulates basic information needed to index a database entry.
   */
  public static class AccountIndex implements Serializable {
    private final String name;
    private final String issuer;

    /**
     * Note that an empty string for {@code issuer} is treated equivalent to a {@code null}
     *
     * @param name the account name (e.g., could be an email address like "bob@example.com")
     * @param issuer the issuer name or {@code null} if unknown
     */
    public AccountIndex(String name, String issuer) {
      Preconditions.checkNotNull(name);
      this.name = name;
      this.issuer = issuer;
      if ((issuer != null) && (issuer.length() == 0)) {
        issuer = null;
      }
    }

    public String getName() {
      return name;
    }

    /** Returns the issuer name, or {@code null} if unknown. */
    public String getIssuer() {
      return issuer;
    }

    /**
     * Returns the account name with the {@code "issuer:"} prefix removed (if present), stripped of
     * any leading or trailing whitespace
     */
    public String getStrippedName() {
      if (Strings.isNullOrEmpty(issuer) || !name.startsWith(issuer + ":")) {
        return name.trim();
      }
      return name.substring(issuer.length() + 1).trim();
    }

    /**
     * If the {@code issuer} is set, returns a fully qualified string of the form {@code
     * "issuer:name"}, e.g., {@code "Google:bob@example.com"}. If no {@code issuer} is set, returns
     * just the account name.
     */
    @Override
    public String toString() {
      if (Strings.isNullOrEmpty(issuer) || (name.startsWith(issuer + ":"))) {
        return name;
      }
      return issuer + ":" + name;
    }

    @Override
    public boolean equals(Object o) {
      if ((o == null) || !(o instanceof AccountIndex)) {
        return false;
      }
      AccountIndex other = (AccountIndex) o;
      boolean issuerMatches =
          (this.issuer == null) ? (other.issuer == null) : this.issuer.equals(other.issuer);
      return this.name.equals(other.name) && issuerMatches;
    }

    @Override
    public int hashCode() {
      if (issuer == null) {
        return name.hashCode();
      }
      return (name + "|" + issuer).hashCode();
    }
  }

  public static String getPrefixedNameFor(String accountName, String issuer) {
    return new AccountIndex(accountName, issuer).toString();
  }

  public AccountDb(Context context) {
    mDatabase = openDatabase(context);

    // Create the table if it doesn't exist
    mDatabase.execSQL(String.format(
        "CREATE TABLE IF NOT EXISTS %s" +
        " (%s INTEGER PRIMARY KEY," +
        " %s TEXT NOT NULL," +
        " %s TEXT NOT NULL," +
        " %s INTEGER DEFAULT %s," +
        " %s INTEGER," +
        " %s INTEGER DEFAULT %s," +
        " %s TEXT DEFAULT NULL," +
        " %s TEXT DEFAULT NULL)",
        TABLE_NAME,
        ID_COLUMN, // Row id, not exposed by this class
        NAME_COLUMN, // Required
        SECRET_COLUMN, // Required
        COUNTER_COLUMN, DEFAULT_HOTP_COUNTER,
        TYPE_COLUMN, // Implicitly required
        PROVIDER_COLUMN, PROVIDER_UNKNOWN,
        // ISSUER and ORIGINAL_NAME are both NULL by default
        ISSUER_COLUMN,
        ORIGINAL_NAME_COLUMN));

    Collection<String> tableColumnNames = listTableColumnNamesLowerCase();
    if (!tableColumnNames.contains(PROVIDER_COLUMN.toLowerCase(Locale.US))) {
      // Migrate from old schema where the PROVIDER_COLUMN wasn't there
      mDatabase.execSQL(String.format(
          "ALTER TABLE %s ADD COLUMN %s INTEGER DEFAULT %s",
          TABLE_NAME, PROVIDER_COLUMN, PROVIDER_UNKNOWN));
    }

    if (!tableColumnNames.contains(ISSUER_COLUMN.toLowerCase(Locale.US))) {
      // Migrate from old schema where the ISSUER_COLUMN wasn't there
      mDatabase.execSQL(String.format(
          "ALTER TABLE %s ADD COLUMN %s TEXT DEFAULT NULL",
          TABLE_NAME, ISSUER_COLUMN));
      autoUpgradeOlderAccountsWithIssuerPrefix();
    }

    if (!tableColumnNames.contains(ORIGINAL_NAME_COLUMN.toLowerCase(Locale.US))) {
      // Migrate from old schema where the ORIGINAL_NAME_COLUMN wasn't there
      mDatabase.execSQL(String.format(
          "ALTER TABLE %s ADD COLUMN %s TEXT DEFAULT NULL",
          TABLE_NAME, ORIGINAL_NAME_COLUMN));
      Log.i(LOCAL_TAG, "Database upgrade complete. Database consistent: " + isDbConsistent());
    }
  }

  /*
   * Tries three times to open database before throwing AccountDbOpenException.
   */
  private SQLiteDatabase openDatabase(Context context) {
    for (int count = 0; true; count++) {
      try {
        return context.openOrCreateDatabase(FileUtilities.DATABASES_PATH, Context.MODE_PRIVATE,
            null);
      } catch (SQLiteException e) {
        if (count < 2) {
          continue;
        } else {
          throw new AccountDbOpenException("Failed to open AccountDb database in three tries.\n"
              + FileUtilities.getFilesystemInfoForErrorString(context), e);
        }
      }
    }
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
  @VisibleForTesting
  static Collection<String> listTableColumnNamesLowerCase(
      SQLiteDatabase database, String tableName) {
    Cursor cursor =
        database.rawQuery(String.format("PRAGMA table_info(%s)", tableName), new String[0]);
    Collection<String> result = Lists.newArrayList();
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
  @VisibleForTesting
  public boolean deleteAllData() {
    mDatabase.delete(AccountDb.TABLE_NAME, null, null);
    return true;
  }

  /**
   * Deletes the whole database (not just its data).
   *
   * @return whether the database was successfully deleted.
   */
  @VisibleForTesting
  public static boolean deleteDatabase(Context context) {
    return context.deleteDatabase(FileUtilities.DATABASES_PATH);
  }

  public boolean indexExists(AccountIndex index) {
    Cursor cursor = getAccountCursor(index);
    try {
      return !cursorIsEmpty(cursor);
    } finally {
      tryCloseCursor(cursor);
    }
  }

  /**
   * Tries to find the specified {@link AccountIndex} in the database, and failing that, will find
   * the first "similar" index in the database. Here, "similar" means that the index has a matching
   * non-null issuer and the stripped name of the account matches.
   *
   * @see AccountIndex#getStrippedName()
   * @return the same {@code index} or a "similar" one with the same issuer that already exists
   *   in the database, or {@code null} if nothing similar is found
   */
  public AccountIndex findSimilarExistingIndex(AccountIndex index) {
    if (indexExists(index)) {
      return index;
    }
    if (index.getIssuer() == null) {
      return null;  // Nothing else can be "similar" when there is no issuer
    }

    Cursor cursor = getIssuerCursor(index.getIssuer());
    try {
      if (cursor == null) {
        return null;
      }
      int nameIndex = cursor.getColumnIndex(AccountDb.NAME_COLUMN);
      while (cursor.moveToNext()) {
        AccountIndex cursorIndex = new AccountIndex(cursor.getString(nameIndex), index.getIssuer());
        if (index.getStrippedName().equals(cursorIndex.getStrippedName())) {
          return cursorIndex;
        }
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return null;
  }

  public String getSecret(AccountIndex index) {
    Cursor cursor = getAccountCursor(index);
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

  /**
   * @return whether the database appears to be consistent with expectations or not
   */
  @VisibleForTesting
  boolean isDbConsistent() {
    boolean result = true;
    Collection<String> tableColumnNames = listTableColumnNamesLowerCase();
    String[] expectedColumnNames = {
        ID_COLUMN,
        NAME_COLUMN,
        SECRET_COLUMN,
        COUNTER_COLUMN,
        TYPE_COLUMN,
        PROVIDER_COLUMN,
        ISSUER_COLUMN,
        ORIGINAL_NAME_COLUMN,
    };
    if (expectedColumnNames.length < tableColumnNames.size()) {
      Log.w(LOCAL_TAG, "Database has extra columns");
      // This doesn't mean the database is necessarily in a bad state
    }
    if (expectedColumnNames.length > tableColumnNames.size()) {
      Log.e(LOCAL_TAG, "Database is missing columns");
      result = false;
    }
    for (String columnName : expectedColumnNames) {
      if (!tableColumnNames.contains(columnName.toLowerCase(Locale.US))) {
        Log.e(LOCAL_TAG, "Database is missing column: " + columnName);
        result = false;
      }
    }

    for (AccountIndex index : getAccounts()) {
      Cursor cursor = mDatabase.query(
          TABLE_NAME, null, whereClause(index), null,
          null, null, null);
      try {
        if (cursor == null) {
          Log.e(LOCAL_TAG, "Failed to get a cursor for account: " + index.toString());
          result = false;
          continue;
        }
        if (cursor.getCount() != 1) {
          Log.e(LOCAL_TAG, "Multiple copies detected for account: " + index.toString());
          result = false;
        }
      } finally {
        tryCloseCursor(cursor);
      }
    }
    return result;
  }

  // TODO: move this method out of this class
  public static Signer getSigningOracle(String secret) {
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

  public Integer getCounter(AccountIndex index) {
    Cursor cursor = getAccountCursor(index);
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

  public void incrementCounter(AccountIndex index) {
    ContentValues values = new ContentValues();
    Integer counter = getCounter(index);
    values.put(COUNTER_COLUMN, counter + 1);
    mDatabase.update(TABLE_NAME, values, whereClause(index), null);
  }

  public OtpType getType(AccountIndex index) {
    Cursor cursor = getAccountCursor(index);
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

  /**
   * Returns the original account name associated with this account (before any renamings),
   * or {@code null} if this information is unavailable.
   */
  public String getOriginalName(AccountIndex index) {
    Cursor cursor = getAccountCursor(index);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        int originalNameIndex = cursor.getColumnIndex(ORIGINAL_NAME_COLUMN);
        if (originalNameIndex < 0) {
          return null;
        }
        return cursor.getString(originalNameIndex);
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return null;
  }

  /**
   * Provides a "best guess" indication of whether this account is for a Google user. Does
   * <b>NOT</b> attempt to match against the set of accounts in use on the Android device via GLS.
   */
  public boolean isGoogleAccount(AccountIndex index) {
    if (GOOGLE_ISSUER_NAME.equalsIgnoreCase(index.getIssuer())) {
      return true;
    } else if (index.getIssuer() != null) {
      return false;
    }

    if (index.getName().equals(GOOGLE_CORP_ACCOUNT_NAME)) {
      return true;
    }

    // See if the account is explicitly marked as a Google account
    Cursor cursor = getAccountCursor(index);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        if (cursor.getInt(cursor.getColumnIndex(PROVIDER_COLUMN)) == PROVIDER_GOOGLE) {
          // The account is marked as source: Google
          return true;
        }
      }
    } finally {
      tryCloseCursor(cursor);
    }

    if (getOriginalName(index) != null) {
      return false; // This account is new, so it would have had the Google issuer if it was ours
    }

    // The account is old and from an unknown source. Could be a Google account added by scanning
    // a QR code or by manually entering a key, and if so, the name should be an email.
    String emailLowerCase = index.getName().toLowerCase(Locale.US);
    return emailLowerCase.endsWith("@gmail.com") || emailLowerCase.endsWith("@google.com");
  }

  /**
   * Tries to find an Authenticator {@link AccountIndex} that corresponds to the Android user
   * account described in {@code accountName}.
   *
   * @param accountName name of an account on the user's device, not necessarily in Authenticator
   * @return a corresponding {@link AccountIndex} or {@code null} if no match could be found
   */
  public AccountIndex findMatchingGoogleAccount(String accountName) {
    String[] accountNamePrefixesToTry = { "", "Google:" };
    for (String prefix : accountNamePrefixesToTry) {
      AccountIndex indexToTry = new AccountIndex(prefix + accountName, GOOGLE_ISSUER_NAME);
      if (indexExists(indexToTry)) {
        return indexToTry;
      }

      // Try finding a Google issued account that used to have the matching name
      for (AccountIndex index : getAccounts()) {
        if (!GOOGLE_ISSUER_NAME.equals(index.getIssuer())) {
          continue; // Only care about Google issued accounts
        }
        String originalName = getOriginalName(index);
        if (accountName.equalsIgnoreCase(prefix + originalName)) {
          return index;
        }
      }

      // No luck yet. It may be a legacy account with no issuer, though.
      indexToTry = new AccountIndex(prefix + accountName, null);
      if (indexExists(indexToTry)) {
        return indexToTry;
      }
    }

    // Google corp account?
    if (accountName.toLowerCase(Locale.US).endsWith("@google.com")) {
      return findGoogleCorpAccount();
    }
    return null;  // Couldn't find anything
  }

  /**
   * Finds the Google corp account in this database.
   *
   * @return the account if it is present or {@code null} if the account does not exist.
   */
  public AccountIndex findGoogleCorpAccount() {
    AccountIndex index = new AccountIndex(GOOGLE_CORP_ACCOUNT_NAME, null);
    return indexExists(index) ? index : null;
  }

  @VisibleForTesting
  static String whereClause(AccountIndex index) {
    Preconditions.checkNotNull(index);
    return NAME_COLUMN + " = " + DatabaseUtils.sqlEscapeString(index.getName()) +
        " AND " + whereClauseForIssuer(index.getIssuer());
  }

  /**
   * Creates a SQL {@code WHERE} clause for an issuer.
   *
   * @param issuer may be {@code null}
   * @return an appropriate SQLite {@code WHERE} clause for this {@code issuer}
   */
  private static String whereClauseForIssuer(String issuer) {
    if (issuer != null) {
      return ISSUER_COLUMN + " = " + DatabaseUtils.sqlEscapeString(issuer);
    }
    return ISSUER_COLUMN + " IS NULL";
  }

  public void delete(AccountIndex index) {
    mDatabase.delete(TABLE_NAME, whereClause(index), null);
  }

  /**
   * Renames the specified account.
   *
   * @return {@code true} if the operation succeeded, {@code false} otherwise (e.g., an account
   *     with {@code newName} and the same {@code issuer} already exists).
   */
  public boolean rename(AccountIndex oldIndex, String newName) {
    Preconditions.checkNotNull(oldIndex);
    Preconditions.checkNotNull(oldIndex.getName());
    Preconditions.checkNotNull(newName);

    if (oldIndex.getName().equals(newName)) {
      // Nothing to do
      return true;
    }

    if (GOOGLE_CORP_ACCOUNT_NAME.equals(oldIndex.getName())) {
      // Renaming the Google corp OTP account breaks reseeding and is thus not supported
      throw new UnsupportedOperationException();
    }

    if (indexExists(new AccountIndex(newName, oldIndex.getIssuer()))) {
      // Don't overwrite an existing account
      return false;
    }

    ContentValues values = new ContentValues();
    values.put(NAME_COLUMN, newName);
    int affectedRows = mDatabase.update(TABLE_NAME, values, whereClause(oldIndex), null);
    if (affectedRows > 1) {
      Log.wtf(LOCAL_TAG, "Unexpectedly changed multiple rows during rename. Database consistent: " +
          isDbConsistent());
    }
    return affectedRows > 0;
  }

  /**
   * Indicates whether an existing {@code secret} would be overwritten if the account described by
   * the specified {@link AccountIndex} were to be added via
   * {@link #add(String, String, OtpType, Integer, Boolean, String)}. Similar (but not identical)
   * indexes may sometimes be overwritten, so the check is not trivial.
   * <p>
   * Note that we never overwrite seeds for accounts with a {@code null} issuer since we will
   * will rename them instead, UNLESS it is a Google Internal 2Factor account.
   *
   * @see #findSimilarExistingIndex(AccountIndex)
   * @return whether an existing record in the database would be overwritten if {@code index} were
   *   to be used in an {@link #add(String, String, OtpType, Integer, Boolean, String)} operation
   */
  public boolean addWillOverwriteExistingSeedFor(AccountIndex index) {
    if ((index.getIssuer() == null) && !index.getName().equals(GOOGLE_CORP_ACCOUNT_NAME)) {
      return false;
    }
    return findSimilarExistingIndex(index) != null;
  }

  /**
   * Adds the specified account into this database. An existing account with the same name and
   * issuer is overwritten by this operation, unless {@code issuer} is {@code null}. If the issuer
   * is {@code null}, then a new account will always be created by appending an incrementing
   * counter to {@code name} until it is unique amongst all account names with {@code null} issuer.
   *
   * @param name the desired account name (e.g., user's email address)
   * @param secret the secret key.
   * @param type hotp vs totp
   * @param counter only important for the hotp type
   * @param googleAccount whether the key is for a Google Account or {@code null} if this
   *        information is not available. In case this operation overwrites an existing account
   *        the value of the flag is not overwritten.
   * @param issuer the {@code issuer} parameter of the QR code if applicable, {@code null} otherwise
   * @return the actual {@link AccountIndex} of the record that was added
   * @throws AccountDbDuplicateLimitException if there are too many accounts with this name already
   */
  public AccountIndex add(String name, String secret, OtpType type, Integer counter,
      Boolean googleAccount, String issuer) {
    Preconditions.checkNotNull(name);
    Preconditions.checkNotNull(secret);
    Preconditions.checkNotNull(type);
    if ((issuer != null) && (issuer.length() == 0)) {
      issuer = null; // Treat an empty issuer as null
    }

    ContentValues values = newContentValuesWith(secret, type, counter, googleAccount);
    AccountIndex indexToAdd = new AccountIndex(name, issuer);
    Log.i(LOCAL_TAG, "Adding account: " + indexToAdd);

    if ((issuer != null) || name.equals(GOOGLE_CORP_ACCOUNT_NAME)) {
      // When an issuer is set, we will overwrite the matching account if it already exists
      // (ditto for "Google Internal 2Factor" accounts, even though they have a null issuer).
      if (issuer != null) {
        values.put(ISSUER_COLUMN, issuer);
        AccountIndex similarIndex = findSimilarExistingIndex(indexToAdd);
        if (similarIndex != null) {
          Log.i(LOCAL_TAG, "Will overwrite similar account: " + similarIndex);
          indexToAdd = similarIndex;
        }
      }
      int updated = mDatabase.update(TABLE_NAME, values, whereClause(indexToAdd), null);
      if (updated == 0) {
        // No matching pre-existing account to update, so insert the new one
        values.put(NAME_COLUMN, name);
        // TODO: Add a test for the ORIGINAL_NAME_COLUMN behavior
        values.put(ORIGINAL_NAME_COLUMN, name);
        mDatabase.insert(TABLE_NAME, null, values);
      } else {
        Log.i(LOCAL_TAG, "Overwrote existing OTP seed for: " + indexToAdd);
      }
      if (!indexToAdd.getName().equals(name)) {
        // We overwrote a similar index with a different name, so now we try to rename it to match
        // the name requested by the add operation (if it is safe to do so)
        rename(indexToAdd, name);
      }
    } else {
      // No issuer is set, so we do not overwrite any existing account
      values.put(NAME_COLUMN, indexToAdd.getName());
      values.put(ORIGINAL_NAME_COLUMN, indexToAdd.getName());
      int tries = 0;
      while (!insertNewAccount(values)) {
        // There was already an account with this name
        tries++;
        if (tries >= MAX_DUPLICATE_NAMES) {
          // TODO: Make this a checked exception, and have the caller show an Alert for it
          throw new AccountDbDuplicateLimitException("Too many accounts with same name: " + name);
        }
        // Rename the account and try again
        indexToAdd = new AccountIndex(name + "(" + tries + ")", issuer);
        values.remove(NAME_COLUMN);
        values.put(NAME_COLUMN, indexToAdd.getName());
      }
    }
    return indexToAdd;
  }

  private ContentValues newContentValuesWith(
      String secret, OtpType type, Integer counter, Boolean googleAccount) {
    ContentValues values = new ContentValues();
    if (secret != null) {
      values.put(SECRET_COLUMN, secret);
    }
    if (type != null) {
      values.put(TYPE_COLUMN, type.ordinal());
    }
    if (counter != null) {
      values.put(COUNTER_COLUMN, counter);
    }
    if (googleAccount != null) {
      values.put(
          PROVIDER_COLUMN,
          (googleAccount.booleanValue()) ? PROVIDER_GOOGLE : PROVIDER_UNKNOWN);
    }
    return values;
  }

  /**
   * Updates the account with the specified {@link AccountIndex} using the provided
   * values.
   *
   * @param index the {@link AccountIndex} pointing to the account to be updated
   * @param secret the new secret key, or use {@code null} to preserve the current one
   * @param type the new {@link OtpType}, hotp vs totp, or {@code null} to leave unchanged
   * @param counter only important for the hotp type. Set to {@code null} to leave unchanged
   * @param googleAccount whether the key is for a Google Account or {@code null} to leave unchanged
   *
   * @return whether the update was successful
   */
  public boolean update(
    AccountIndex index, String secret, OtpType type, Integer counter, Boolean googleAccount) {
    Preconditions.checkNotNull(index);
    Log.d(LOCAL_TAG, "Updating account: " + index);

    ContentValues values = newContentValuesWith(secret, type, counter, googleAccount);
    int affectedRows = mDatabase.update(TABLE_NAME, values, whereClause(index), null);
    if (affectedRows > 1) {
      Log.wtf(LOCAL_TAG, "Unexpectedly changed multiple rows during update. Database consistent: " +
          isDbConsistent());
    }
    return  affectedRows > 0;
  }

  /**
   * Swaps the value of the {@link #ID_COLUMN} associated with the passed two AccountIndexes.
   *
   * @param firstIndex the {@link AccountIndex} to be swapped
   * @param secondIndex the {@link AccountIndex} to be swapped
   */
  public void swapId(AccountIndex firstIndex, AccountIndex secondIndex)
      throws AccountDbIdUpdateFailureException{
    mDatabase.beginTransaction();
    try {
      int firstId = getId(firstIndex);
      int secondId = getId(secondIndex);

      // Update the id of the second index to avoid the unique key constraint
      ContentValues valuesForTemporalId = new ContentValues();
      valuesForTemporalId.put(ID_COLUMN, INVALID_ID);
      mDatabase
          .updateWithOnConflict(TABLE_NAME, valuesForTemporalId, whereClause(secondIndex), null,
              SQLiteDatabase.CONFLICT_ROLLBACK);

      ContentValues valuesForSecondId = new ContentValues();
      valuesForSecondId.put(ID_COLUMN, secondId);
      mDatabase.updateWithOnConflict(TABLE_NAME, valuesForSecondId, whereClause(firstIndex), null,
          SQLiteDatabase.CONFLICT_ROLLBACK);

      ContentValues valuesForFirstId = new ContentValues();
      valuesForFirstId.put(ID_COLUMN, firstId);
      mDatabase.updateWithOnConflict(TABLE_NAME, valuesForFirstId, whereClause(secondIndex), null,
          SQLiteDatabase.CONFLICT_ROLLBACK);
      mDatabase.setTransactionSuccessful();
    } catch (SQLiteException e) {
      throw new AccountDbIdUpdateFailureException(
          String.format("Updating the Id failed for %s and %s", firstIndex, secondIndex), e);
    } finally {
      mDatabase.endTransaction();
    }
  }

  @VisibleForTesting
  int getId(AccountIndex index) {
    Cursor cursor = getAccountCursor(index);
    try {
      if (!cursorIsEmpty(cursor)) {
        cursor.moveToFirst();
        return cursor.getInt(cursor.getColumnIndex(ID_COLUMN));
      }
    } finally {
      tryCloseCursor(cursor);
    }
    return INVALID_ID;
  }

  private boolean insertNewAccount(ContentValues values) {
    Preconditions.checkNotNull(values);
    Preconditions.checkNotNull(values.get(NAME_COLUMN));
    AccountIndex index =
        new AccountIndex((String) values.get(NAME_COLUMN), (String) values.get(ISSUER_COLUMN));
    if (indexExists(index)) {
      return false;  // An account with this name already exists
    }
    return mDatabase.insert(TABLE_NAME, null, values) != -1;
  }

  private Cursor getAccountCursor(AccountIndex index) {
    return mDatabase.query(TABLE_NAME, null, whereClause(index),
        null, null, null, null);
  }

  private Cursor getIssuerCursor(String issuer) {
    return mDatabase.query(TABLE_NAME, null, whereClauseForIssuer(issuer),
        null, null, null, null);
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
   * Lists the names of all accounts.
   */
  public List<AccountIndex> getAccounts() {
    Cursor cursor = mDatabase.query(TABLE_NAME, null, null, null, null, null, null, null);

    try {
      if (cursorIsEmpty(cursor)) {
        return ImmutableList.of();
      }

      int nameCount = cursor.getCount();
      int nameIndex = cursor.getColumnIndex(AccountDb.NAME_COLUMN);
      int issuerIndex = cursor.getColumnIndex(AccountDb.ISSUER_COLUMN);

      ImmutableList.Builder<AccountIndex> resultBuilder = ImmutableList.builder();
      for (int i = 0; i < nameCount; ++i) {
        cursor.moveToPosition(i);
        String name = cursor.getString(nameIndex);
        String issuer = null;
        if (issuerIndex >= 0) {
          issuer = cursor.getString(issuerIndex);
        }
        resultBuilder.add(new AccountIndex(name, issuer));
      }
      return resultBuilder.build();
    } finally {
      tryCloseCursor(cursor);
    }
  }

  private void autoUpgradeOlderAccountsWithIssuerPrefix() {
    for (AccountIndex index : getAccounts()) {
      if (index.getIssuer() != null) {
        Log.wtf(LOCAL_TAG, "Existing new-style account detected during account upgrade process: "
            + index.toString());
        continue;
      }

      for (String issuer : AUTO_UPGRADE_ISSUERS) {
        if (index.getName().startsWith(issuer + ":")) {
          Log.d(LOCAL_TAG, "Auto-upgrading old-style account: " + index.toString());
          ContentValues values = new ContentValues();
          values.put(ISSUER_COLUMN, issuer);
          int affectedRows = mDatabase.update(TABLE_NAME, values, whereClause(index), null);
          if (affectedRows > 1) {
            Log.wtf(LOCAL_TAG, "Unexpectedly changed multiple rows while auto-upgrading account: "
                + index.toString());
          }
          continue;
        }
      }
    }
  }

  private static class AccountDbOpenException extends RuntimeException {
    public AccountDbOpenException(String message, Exception e) {
      super(message, e);
    }
  }

  /**
   * Exception thrown when the there are too many accounts with duplicate names (which can only
   * happen for accounts with a {@code null} issuer parameter).
   */
  public static class AccountDbDuplicateLimitException extends RuntimeException {
    public AccountDbDuplicateLimitException(String message, Exception e) {
      super(message, e);
    }

    public AccountDbDuplicateLimitException(String message) {
      super(message);
    }
  }

  /**
   * Exception thrown when updating the values of {@link #ID_COLUMN} failed
   */
  public static class AccountDbIdUpdateFailureException extends Exception {
    public  AccountDbIdUpdateFailureException(String message) {
      super(message);
    }

    public AccountDbIdUpdateFailureException(String message, Exception e) {
      super(message, e);
    }
  }
}
