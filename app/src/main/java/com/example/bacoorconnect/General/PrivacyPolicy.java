package com.example.bacoorconnect.General;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.example.bacoorconnect.R;

public class PrivacyPolicy extends AppCompatActivity {

    private NestedScrollView scrollView;
    private TextView termsTextView;
    private CheckBox agreeCheckBox;
    private Button proceedButton;
    private boolean hasScrolledToBottom = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_privacy_policy);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.PRIVPOL_ACT), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

                scrollView = findViewById(R.id.termsScrollView);
                termsTextView = findViewById(R.id.termsTextView);
                agreeCheckBox = findViewById(R.id.agreeCheckBox);
                proceedButton = findViewById(R.id.proceedButton);

                agreeCheckBox.setEnabled(false);
                proceedButton.setEnabled(false);

                scrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
                    @Override
                    public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                        if (scrollY >= (v.getChildAt(0).getMeasuredHeight() - v.getMeasuredHeight())) {
                            if (!hasScrolledToBottom) {
                                hasScrolledToBottom = true;
                                agreeCheckBox.setEnabled(true);
                                Toast.makeText(PrivacyPolicy.this, "You can now accept the Privacy Policy.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

                agreeCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    proceedButton.setEnabled(isChecked);
                });

                proceedButton.setOnClickListener(v -> {
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("privacyAccepted", true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                });
            }
        }