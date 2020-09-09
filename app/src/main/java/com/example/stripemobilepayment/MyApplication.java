package com.example.stripemobilepayment;

import android.app.Application;

import com.stripe.android.BuildConfig;
import com.stripe.android.PaymentConfiguration;


public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PaymentConfiguration.init(getApplicationContext(), BuildConfig.PublishableKey);
    }
}
