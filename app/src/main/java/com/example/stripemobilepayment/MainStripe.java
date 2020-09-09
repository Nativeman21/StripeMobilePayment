package com.example.stripemobilepayment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.stripe.android.ApiResultCallback;
import com.stripe.android.CustomerSession;
import com.stripe.android.PaymentConfiguration;
import com.stripe.android.PaymentIntentResult;
import com.stripe.android.PaymentSession;
import com.stripe.android.PaymentSession.PaymentSessionListener;
import com.stripe.android.PaymentSessionConfig;
import com.stripe.android.PaymentSessionData;
import com.stripe.android.Stripe;
import com.stripe.android.model.ConfirmPaymentIntentParams;
import com.stripe.android.model.PaymentIntent;
import com.stripe.android.model.PaymentMethod;
import com.stripe.android.model.PaymentMethod.Card;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MainStripe extends AppCompatActivity {
    private final int RC_SIGN_IN = 1;
    private FirebaseUser currentUser;
    private PaymentSession paymentSession;
    private PaymentMethod selectedPaymentMethod;
    private Stripe stripe;
    PaymentMethod paymentMethod;

    TextView checkoutSummary, paymentMethodTv, greeting;
    Button loginButton, payButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.activity_main_stripe);

        currentUser = FirebaseAuth.getInstance().getCurrentUser();
        stripe = new Stripe(getApplicationContext(),
                PaymentConfiguration.getInstance(getApplicationContext()).getPublishableKey());

        loginButton = findViewById(R.id.loginButton);
        payButton = findViewById(R.id.payButton);
        checkoutSummary = findViewById(R.id.checkoutSummary);
        greeting = findViewById(R.id.greeting);
        paymentMethodTv = findViewById(R.id.paymentmethod);

        loginButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                ArrayList<AuthUI.IdpConfig> providers = new ArrayList<>();
                providers.addAll( Arrays.asList(new AuthUI.IdpConfig.EmailBuilder().build()));

                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setAvailableProviders(providers)
                                .build(),RC_SIGN_IN);
            }
        });
        payButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                confirmPayment(selectedPaymentMethod.id);
            }
        });

        paymentMethodTv.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                paymentSession.presentPaymentMethodSelection(selectedPaymentMethod.id);
            }
        });
        showUI();
    }

    private void showUI() {
        if(currentUser != null) {
            loginButton.setVisibility(View.GONE);

            greeting.setVisibility(View.VISIBLE);
            checkoutSummary.setVisibility(View.VISIBLE);
            payButton.setVisibility(View.VISIBLE);
            paymentMethodTv.setVisibility(View.VISIBLE);

            greeting.setText("Hello" + currentUser.getDisplayName());

            setupPaymentSession();
        } else {
            loginButton.setVisibility(View.VISIBLE);

            greeting.setVisibility(View.VISIBLE);
            checkoutSummary.setVisibility(View.GONE);
            payButton.setVisibility(View.GONE);
            paymentMethodTv.setVisibility(View.GONE);

            payButton.setEnabled(false);

        }
    }

    private void confirmPayment(final String paymentMethodId) {
        CollectionReference paymentCollection = FirebaseFirestore.getInstance().collection("stripe_customers")
                .document(currentUser.getUid()).collection("payments");

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("amount", 8800);
        hashMap.put("currency", "cad");

        paymentCollection.add(hashMap)
                .addOnSuccessListener(new OnSuccessListener() {

            public void onSuccess(Object listener) {
                this.onSuccesss((DocumentReference)listener);
            }

            public void onSuccesss(DocumentReference documentReference) {
                StringBuilder thisUser = (new StringBuilder()).append("DocumentSnapshot added with ID: ");

                Log.d("paymentsss", thisUser.append(documentReference.getId()).toString());

                documentReference.addSnapshotListener(new EventListener() {
                    public void onEvent(Object listener, FirebaseFirestoreException e) {
                        this.onEvent((DocumentSnapshot)listener, e);
                    }

                    public final void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.w("paymentsss", "Listen failed.", (Throwable)e);
                        } else {
                            if (snapshot != null && snapshot.exists()) {
                                Log.d("paymentsss", "Current data: " + snapshot.getData());

                                Object clientSecret = snapshot.getData().get("client_secret");
                                Log.d("paymentsss", "Create paymentIntent returns " + clientSecret);
                                if (clientSecret != null) {

                                    stripe.confirmPayment(MainStripe.this,ConfirmPaymentIntentParams.createWithPaymentMethodId(
                                            paymentMethodId, clientSecret.toString()));

                                    checkoutSummary.setText((CharSequence)"Thank you for your payment");
                                    Toast.makeText(getApplicationContext(), (CharSequence)"Payment Done!!", Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Log.e("paymentsss", "Current payment intent : null");
                                payButton.setEnabled(true);
                            }

                        }
                    }
                });
            }
        }).addOnFailureListener((OnFailureListener)(new OnFailureListener() {
            public final void onFailure(@NotNull Exception e) {
                Log.w("paymentsss", "Error adding document", (Throwable)e);
                payButton.setEnabled(true);
            }
        }));
    }

    private void setupPaymentSession() {
        CustomerSession.initCustomerSession(this, new FirebaseEphemeralKeyProvider());

        PaymentSessionConfig config = new PaymentSessionConfig.Builder()
            .setShippingMethodsRequired(false)
            .setShippingInfoRequired(false)
            .build();

        paymentSession = new PaymentSession(this, config);

        paymentSession.init(new PaymentSessionListener() {
            public void onPaymentSessionDataChanged(@NotNull PaymentSessionData data) {

                Log.d("PaymentSession1", "11PaymentSession has changed: " + data);
                Log.d("PaymentSession11", "1111 " + data.isPaymentReadyToCharge() + " <> " + data.getPaymentMethod());
                if (data.isPaymentReadyToCharge()) {
                    Log.d("PaymentSession2", "222Ready to charge");

                    payButton.setEnabled(true);
                    PaymentMethod paymentMethod = data.getPaymentMethod();
                    if (paymentMethod != null) {
                        Log.d("PaymentSession3", "333PaymentMethod " + paymentMethod + " selected");

                        StringBuilder thisUser = new StringBuilder();
                        Card card = paymentMethod.card;

                        thisUser = thisUser.append(card != null ? card.brand : null).append(" card ends with ");
                        paymentMethodTv.setText((CharSequence)thisUser.append(card != null ? card.last4 : null).toString());

                        selectedPaymentMethod = paymentMethod;
                    }
                }
            }

            public void onCommunicatingStateChanged(boolean isCommunicating) {
                Log.d("PaymentSession4", "444isCommunicating " + isCommunicating);
            }

            public void onError(int errorCode, @NotNull String errorMessage) {
                Log.e("PaymentSession5", "555onError: " + errorCode + ", " + errorMessage);
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN && resultCode == Activity.RESULT_OK) {

            currentUser = FirebaseAuth.getInstance().getCurrentUser();
            paymentSession.handlePaymentData(requestCode, resultCode, data);

            stripe.onPaymentResult(requestCode, data, new PaymentResultCallback(this));
            Log.d("Login", "User ${currentUser?.displayName} has signed in.");
            showUI();
        }
    }

    private static final class PaymentResultCallback
            implements ApiResultCallback<PaymentIntentResult> {
        @NonNull private final WeakReference<MainStripe> activityRef;

        PaymentResultCallback(@NonNull MainStripe activity) {
            activityRef = new WeakReference<>(activity);
        }

        @Override
        public void onSuccess(@NonNull PaymentIntentResult result) {
            final MainStripe activity = activityRef.get();
            if (activity == null) {
                return;
            }

            PaymentIntent paymentIntent = result.getIntent();
            PaymentIntent.Status status = paymentIntent.getStatus();
            if (status == PaymentIntent.Status.Succeeded) {
                // Payment completed successfully
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                //activity.displayAlert("Payment completed", gson.toJson(paymentIntent), true);
            } else if (status == PaymentIntent.Status.RequiresPaymentMethod) {
                // Payment failed
                //activity.displayAlert("Payment failed", Objects.requireNonNull(paymentIntent.getLastPaymentError()).getMessage(), false);
            }
        }

        @Override
        public void onError(@NonNull Exception e) {
            final MainStripe activity = activityRef.get();
        }
    }
}