package com.example.bacoorconnect.General;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.example.bacoorconnect.R;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class NavigationHeader {

    private static final String TAG = "NavigationHeader";

    public static void setupNavigationHeader(Activity activity, NavigationView navigationView) {
        View headerView = navigationView.getHeaderView(0);

        ImageView imageView = headerView.findViewById(R.id.imageView);
        TextView navUsername = headerView.findViewById(R.id.navUsername);
        TextView navEmail = headerView.findViewById(R.id.navEmail);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user != null) {
            String userId = user.getUid();

            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        Log.d(TAG, "User document exists.");

                        String username = dataSnapshot.child("firstName").getValue(String.class);
                        String email = dataSnapshot.child("email").getValue(String.class);
                        String profileImageUrl = dataSnapshot.child("profileImage").getValue(String.class);

                        navUsername.setText(username != null ? username : "User");
                        navEmail.setText(email != null ? email : "Email not set");

                        if (profileImageUrl != null && !profileImageUrl.isEmpty()) {
                            Glide.with(activity).load(profileImageUrl).circleCrop().into(imageView);
                            Log.d(TAG, "Loaded profile image with Glide.");
                        } else {
                            imageView.setImageResource(R.drawable.profile);
                            Log.d(TAG, "Set default profile image.");
                        }

                    } else {
                        Log.w(TAG, "User document does not exist.");
                        setGuestHeader(navUsername, navEmail, imageView);
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.e(TAG, "Error fetching user document: ", databaseError.toException());
                    setGuestHeader(navUsername, navEmail, imageView);
                }
            });

        } else {
            Log.w(TAG, "No user is currently signed in.");
            setGuestHeader(navUsername, navEmail, imageView);
        }
    }

    private static void setGuestHeader(TextView navUsername, TextView navEmail, ImageView imageView) {
        navUsername.setText("Guest");
        navEmail.setText("Limited Access Mode");
        imageView.setImageResource(R.drawable.profile);
    }


}
