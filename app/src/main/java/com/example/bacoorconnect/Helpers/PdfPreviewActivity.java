package com.example.bacoorconnect.Helpers;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;

import com.example.bacoorconnect.R;

import java.io.File;

public class PdfPreviewActivity extends AppCompatActivity {

    private ImageView pdfPageView;
    private TextView pageNumberText;
    private Button printButton;
    private Button shareButton;
    private ImageButton prevPageButton;
    private ImageButton nextPageButton;

    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor fileDescriptor;
    private String filePath;
    private int currentPageNumber = 0;
    private int pageCount = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_preview);


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        toolbar.setNavigationOnClickListener(v -> finish());

        pdfPageView = findViewById(R.id.pdf_page_view);
        pageNumberText = findViewById(R.id.page_number_text);
        printButton = findViewById(R.id.print_button);
        shareButton = findViewById(R.id.share_button);
        prevPageButton = findViewById(R.id.prev_page_button);
        nextPageButton = findViewById(R.id.next_page_button);

        // Get PDF file path from intent
        filePath = getIntent().getStringExtra("PDF_FILE_PATH");

        if (filePath == null || filePath.isEmpty()) {
            Toast.makeText(this, "Error: PDF file not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Set up buttons
        printButton.setOnClickListener(v -> printPdf());
        shareButton.setOnClickListener(v -> sharePdf());
        prevPageButton.setOnClickListener(v -> showPage(currentPageNumber - 1));
        nextPageButton.setOnClickListener(v -> showPage(currentPageNumber + 1));

        try {
            openPdfRenderer();
            showPage(0);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void openPdfRenderer() throws Exception {
        File file = new File(filePath);
        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);

        if (fileDescriptor != null) {
            pdfRenderer = new PdfRenderer(fileDescriptor);
            pageCount = pdfRenderer.getPageCount();

            // Update UI
            updatePageButtonsState();
        }
    }

    private void showPage(int index) {
        if (index < 0 || index >= pageCount) {
            return;
        }

        // Close the current page if it exists
        if (currentPage != null) {
            currentPage.close();
        }

        // Open the specified page
        currentPage = pdfRenderer.openPage(index);
        currentPageNumber = index;

        // Render the page to a bitmap
        Bitmap bitmap = Bitmap.createBitmap(
                currentPage.getWidth(),
                currentPage.getHeight(),
                Bitmap.Config.ARGB_8888);

        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);

        // Display the bitmap
        pdfPageView.setImageBitmap(bitmap);

        // Update page number text
        pageNumberText.setText(String.format("Page %d of %d", currentPageNumber + 1, pageCount));

        // Update button states
        updatePageButtonsState();
    }

    private void updatePageButtonsState() {
        prevPageButton.setEnabled(currentPageNumber > 0);
        prevPageButton.setAlpha(currentPageNumber > 0 ? 1.0f : 0.5f);

        nextPageButton.setEnabled(currentPageNumber < pageCount - 1);
        nextPageButton.setAlpha(currentPageNumber < pageCount - 1 ? 1.0f : 0.5f);
    }

    private void printPdf() {
        File file = new File(filePath);
        Uri uri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".provider",
                file);

        Intent printIntent = new Intent(Intent.ACTION_SEND);
        printIntent.setType("application/pdf");
        printIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(printIntent, "Print PDF"));
    }

    private void sharePdf() {
        File file = new File(filePath);
        Uri uri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".provider",
                file);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, "Share PDF Report"));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (currentPage != null) {
                currentPage.close();
            }

            if (pdfRenderer != null) {
                pdfRenderer.close();
            }

            if (fileDescriptor != null) {
                fileDescriptor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}