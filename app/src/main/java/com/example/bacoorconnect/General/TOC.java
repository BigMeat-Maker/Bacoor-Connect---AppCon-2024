package com.example.bacoorconnect.General;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
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

public class TOC extends AppCompatActivity {

    private NestedScrollView scrollView;
    private TextView termsTextView;
    private CheckBox TOCCheckBox;
    private Button proceedButton;
    private boolean hasScrolledToBottom = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_toc);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.TOC_ACT), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        // Initialize views
        scrollView = findViewById(R.id.termsScrollView);
        termsTextView = findViewById(R.id.termsTextView);
        TOCCheckBox = findViewById(R.id.TOCCheckBox);
        proceedButton = findViewById(R.id.proceedButton);

        TOCCheckBox.setEnabled(false);
        proceedButton.setEnabled(false);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            termsTextView.setText(Html.fromHtml(getString(R.string.toc), Html.FROM_HTML_MODE_COMPACT));
        } else {
            termsTextView.setText(Html.fromHtml(getString(R.string.toc)));
        }



        scrollView.setOnScrollChangeListener(new NestedScrollView.OnScrollChangeListener() {
            @Override
            public void onScrollChange(NestedScrollView v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
                if (scrollY >= (v.getChildAt(0).getMeasuredHeight() - v.getMeasuredHeight())) {
                    if (!hasScrolledToBottom) {
                        hasScrolledToBottom = true;
                        TOCCheckBox.setEnabled(true);
                    }
                }
            }
        });

        TOCCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            proceedButton.setEnabled(isChecked);
        });

        proceedButton.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("tocAccepted", true);
            setResult(RESULT_OK, resultIntent);
            finish();
        });


    }
}