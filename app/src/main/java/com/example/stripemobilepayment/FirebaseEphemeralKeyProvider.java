package com.example.stripemobilepayment;


import androidx.annotation.NonNull;
import androidx.annotation.Size;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.stripe.android.EphemeralKeyProvider;
import com.stripe.android.EphemeralKeyUpdateListener;
import com.stripe.jetbrains.annotations.NotNull;

import java.util.HashMap;
public class FirebaseEphemeralKeyProvider implements EphemeralKeyProvider {

    @Override
    public void createEphemeralKey(@NotNull @Size(min = 4) String apiVersion,
                                   @NotNull EphemeralKeyUpdateListener ephemeralKeyUpdateListener) {

        HashMap<String, String> data = new HashMap<>();
        data.put("api_version", apiVersion);
        FirebaseFunctions.getInstance()
                .getHttpsCallable("createEphemeralKey")
                .call(data)
                .continueWith(new Continuation<HttpsCallableResult, Object>() {

            @Override
            public Object then(@NonNull Task<HttpsCallableResult> task) throws Exception {
                if(task.isSuccessful())
                    ephemeralKeyUpdateListener.onKeyUpdate(task.getResult().getData().toString());
                return null;
            }
        });
    }
}
