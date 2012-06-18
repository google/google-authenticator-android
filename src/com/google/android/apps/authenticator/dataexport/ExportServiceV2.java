/*
 * Copyright 2011 Google Inc. All Rights Reserved.
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

package com.google.android.apps.authenticator.dataexport;

import com.google.android.apps.authenticator.testability.DependencyInjector;

import android.app.Service;
import android.content.Intent;
import android.content.pm.Signature;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;

/**
 * {@link Service} that exports the accounts database, and the key material and settings.
 * Data is exported using {@link Exporter} and only exported to authorized callers
 * as determined by  {@link SignatureBasedAuthorizationChecker}.
 *
 * @author klyubin@google.com (Alex Klyubin)
 */
public class ExportServiceV2 extends Service {

  private static final Signature APPS_KEY = new Signature(
      "308204433082032ba003020102020900c2e08746644a308d300d06092a864886f70d01010405003074310b3"
      + "009060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d"
      + "4d6f756e7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e0603550"
      + "40b1307416e64726f69643110300e06035504031307416e64726f6964301e170d30383038323132333133"
      + "33345a170d3336303130373233313333345a3074310b30090603550406130255533113301106035504081"
      + "30a43616c69666f726e6961311630140603550407130d4d6f756e7461696e205669657731143012060355"
      + "040a130b476f6f676c6520496e632e3110300e060355040b1307416e64726f69643110300e06035504031"
      + "307416e64726f696430820120300d06092a864886f70d01010105000382010d00308201080282010100ab"
      + "562e00d83ba208ae0a966f124e29da11f2ab56d08f58e2cca91303e9b754d372f640a71b1dcb130967624"
      + "e4656a7776a92193db2e5bfb724a91e77188b0e6a47a43b33d9609b77183145ccdf7b2e586674c9e1565b"
      + "1f4c6a5955bff251a63dabf9c55c27222252e875e4f8154a645f897168c0b1bfc612eabf785769bb34aa7"
      + "984dc7e2ea2764cae8307d8c17154d7ee5f64a51a44a602c249054157dc02cd5f5c0e55fbef8519fbe327"
      + "f0b1511692c5a06f19d18385f5c4dbc2d6b93f68cc2979c70e18ab93866b3bd5db8999552a0e3b4c99df5"
      + "8fb918bedc182ba35e003c1b4b10dd244a8ee24fffd333872ab5221985edab0fc0d0b145b6aa192858e79"
      + "020103a381d93081d6301d0603551d0e04160414c77d8cc2211756259a7fd382df6be398e4d786a53081a"
      + "60603551d2304819e30819b8014c77d8cc2211756259a7fd382df6be398e4d786a5a178a4763074310b30"
      + "09060355040613025553311330110603550408130a43616c69666f726e6961311630140603550407130d4"
      + "d6f756e7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e06035504"
      + "0b1307416e64726f69643110300e06035504031307416e64726f6964820900c2e08746644a308d300c060"
      + "3551d13040530030101ff300d06092a864886f70d010104050003820101006dd252ceef85302c360aaace"
      + "939bcff2cca904bb5d7a1661f8ae46b2994204d0ff4a68c7ed1a531ec4595a623ce60763b167297a7ae35"
      + "712c407f208f0cb109429124d7b106219c084ca3eb3f9ad5fb871ef92269a8be28bf16d44c8d9a08e6cb2"
      + "f005bb3fe2cb96447e868e731076ad45b33f6009ea19c161e62641aa99271dfd5228c5c587875ddb7f452"
      + "758d661f6cc0cccb7352e424cc4365c523532f7325137593c4ae341f4db41edda0d0b1071a7c440f0fe9e"
      + "a01cb627ca674369d084bd2fd911ff06cdbf2cfa10dc0f893ae35762919048c7efc64c7144178342f7058"
      + "1c9de573af55b390dd7fdb9418631895d5f759f30112687ff621410c069308a");


  private static final String AUTHORIZED_PACKAGE = "com.google.android.apps.authenticator2";

  // @VisibleForTesting
  AuthorizationChecker mAuthorizationChecker;

  // @VisibleForTesting
  Exporter mExporter;

  private ExporterStub mExporterStub;

  @Override
  public void onCreate() {
    super.onCreate();

    // The conditional assignment below is to facilitate dependency injection from unit tests
    if (mAuthorizationChecker == null) {
      mAuthorizationChecker =
          new SignatureBasedAuthorizationChecker(
              DependencyInjector.getPackageManager(), AUTHORIZED_PACKAGE, APPS_KEY);
    }
    if (mExporter == null) {
      mExporter =
          new Exporter(
              DependencyInjector.getAccountDb(),
              null);
    }
    if (mExporterStub == null) {
      mExporterStub = new ExporterStub(mAuthorizationChecker, mExporter);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mExporterStub.asBinder();
  }

  private static class ExporterStub extends IExportServiceV2.Stub {

    private final AuthorizationChecker mAuthorizationChecker;
    private final Exporter mExporter;

    private ExporterStub(
        AuthorizationChecker authorizationChecker,
        Exporter exporter) {
      mAuthorizationChecker = authorizationChecker;
      mExporter = exporter;
    }

    @Override
    public Bundle getData() {
      mAuthorizationChecker.checkAuthorization(Binder.getCallingUid());
      return mExporter.getData();
    }

    @Override
    public void onImportSucceeded() {
      mAuthorizationChecker.checkAuthorization(Binder.getCallingUid());

      // Delete all accounts now that they are in the "new" app
      DependencyInjector.getAccountDb().deleteAllData();
    }
  }
}
