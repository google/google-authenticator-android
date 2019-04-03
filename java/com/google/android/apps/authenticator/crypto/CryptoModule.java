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

package com.google.android.apps.authenticator.crypto;

import dagger.Module;
import dagger.Provides;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.inject.Named;

/**
 * Dagger module for crypto package.
 */
@Module(
    library = true
)
public class CryptoModule {

  @Provides @Named("MD5")
  public MessageDigest provideMessageDigestMd5() {
    try {
      return MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Provides @Named("SHA256")
  public MessageDigest provideMessageDigestSha256() {
    try {
      return MessageDigest.getInstance("SHA256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
