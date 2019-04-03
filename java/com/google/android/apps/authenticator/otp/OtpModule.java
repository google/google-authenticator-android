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

import android.content.Context;
import com.google.android.apps.authenticator.AuthenticatorActivity;
import com.google.android.apps.authenticator.common.ApplicationContext;
import com.google.android.apps.authenticator.time.Clock;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Dagger module for the otp package.
 */
@Module(
    library = true,
    complete = false
)
public class OtpModule {

  @Provides @Singleton
  public TotpClock providesTotpClock(@ApplicationContext Context applicationContext, Clock clock) {
    return new TotpClock(applicationContext, clock);
  }

  @Provides @Singleton
  public OtpSource providesOtpSource(AccountDb accountDb, TotpClock totpClock) {
    return new OtpProvider(accountDb, totpClock);
  }

  @Provides
  public TotpCounter providesTotpCounter(OtpSource otpSource) {
    return otpSource.getTotpCounter();
  }

  @Provides
  public TotpCountdownTask providesTotpCountdownTask(TotpCounter totpCounter, TotpClock totpClock) {
    return new TotpCountdownTask(totpCounter, totpClock,
        AuthenticatorActivity.TOTP_COUNTDOWN_REFRESH_PERIOD_MILLIS);
  }

  @Provides @Singleton
  public AccountDb providesAccountDb(@ApplicationContext Context applicationContext) {
    return new AccountDb(applicationContext);
  }
}
