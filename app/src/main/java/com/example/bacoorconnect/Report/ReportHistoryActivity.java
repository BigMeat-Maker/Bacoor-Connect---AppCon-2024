package com.example.bacoorconnect.Report;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // Import ImageView
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.General.NavigationHandler;
import com.example.bacoorconnect.General.NavigationHeader;
import com.example.bacoorconnect.Helpers.PdfPreviewActivity;
import com.example.bacoorconnect.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.database.DatabaseReference;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ReportHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ReportHistoryActivity";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyTextView;
    private MaterialButton exportButton;
    private MaterialButton printButton;
    private List<ReportItem> reportItems;
    private ImageView menuIcon;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private DatabaseReference databaseReference;

    // Dashboard views
    private View totalReportsCard;
    private View resolvedReportsCard;
    private View pendingReportsCard;
    private View inProgressReportsCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: Activity created");
        setContentView(R.layout.activity_report_history);

        menuIcon = findViewById(R.id.menu_icon);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        NavigationHeader.setupNavigationHeader(this, navigationView);

        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });

        // Initialize views
        recyclerView = findViewById(R.id.report_recycler_view);
        progressBar = findViewById(R.id.progress_bar);
        emptyTextView = findViewById(R.id.empty_view);
        exportButton = findViewById(R.id.export_button);
        printButton = findViewById(R.id.print_button);

        // Initialize dashboard cards
        totalReportsCard = findViewById(R.id.total_reports_card);
        resolvedReportsCard = findViewById(R.id.resolved_reports_card);
        pendingReportsCard = findViewById(R.id.pending_reports_card);
        inProgressReportsCard = findViewById(R.id.in_progress_reports_card);

        // Set up RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Set up button click listeners
        exportButton.setOnClickListener(v -> exportReport());
        printButton.setOnClickListener(v -> printReport());

        databaseReference = FirebaseDatabase.getInstance().getReference("Report");
        Log.d(TAG, "onCreate: Firebase database reference initialized");


        // Load report history data
        loadReportHistory();

        menuIcon = findViewById(R.id.menu_icon);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        menuIcon.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(navigationView)) {
                drawerLayout.closeDrawer(navigationView);
            } else {
                drawerLayout.openDrawer(navigationView);
            }
        });
        navigationView.setNavigationItemSelectedListener(item -> {
            NavigationHandler navigationHandler = new NavigationHandler(this, drawerLayout);
            navigationHandler.handleMenuSelection(item);
            drawerLayout.closeDrawer(navigationView);
            return true;
        });
    }

    private void loadReportHistory() {
        Log.d(TAG, "loadReportHistory: Loading reports from Firebase");

        // Show loading indicator
        progressBar.setVisibility(View.VISIBLE);

        // Fetch reports from Firebase
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "onDataChange: Data received from Firebase");
                reportItems = new ArrayList<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    ReportItem reportItem = snapshot.getValue(ReportItem.class);
                    if (reportItem != null) {
                        reportItems.add(reportItem);
                        // Updated log to use getCategory()
                        Log.d(TAG, "onDataChange: Added report item: " + reportItem.getCategory() + " at " + reportItem.getLocation());
                    } else {
                        Log.w(TAG, "onDataChange: Received null report item from snapshot");
                    }
                }

                // Hide loading indicator
                progressBar.setVisibility(View.GONE);
                Log.d(TAG, "onDataChange: Progress bar hidden");

                if (reportItems.isEmpty()) {
                    Log.d(TAG, "onDataChange: No reports found");
                    recyclerView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.VISIBLE);
                } else {
                    Log.d(TAG, "onDataChange: Reports loaded successfully. Count: " + reportItems.size());
                    recyclerView.setVisibility(View.VISIBLE);
                    emptyTextView.setVisibility(View.GONE);

                    // Set up adapter with data
                    ReportHistoryAdapter adapter = new ReportHistoryAdapter(reportItems);
                    recyclerView.setAdapter(adapter);
                    Log.d(TAG, "onDataChange: RecyclerView adapter set");

                    // Update dashboard with report statistics
                    updateDashboard();
                    Log.d(TAG, "onDataChange: Dashboard updated");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Handle error
                Log.e(TAG, "onCancelled: Failed to load reports", databaseError.toException());
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ReportHistoryActivity.this, "Failed to load reports.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateDashboard() {
        Log.d(TAG, "updateDashboard: Updating dashboard statistics");
        int totalReports = reportItems.size();
        int positiveReports = 0;
        int negativeReports = 0;

        for (ReportItem item : reportItems) {
            if (item.getUpvotes() > item.getDownvotes()) {
                positiveReports++;
            } else if (item.getDownvotes() > item.getUpvotes()) {
                negativeReports++;
            }
        }

        updateDashboardCard(totalReportsCard, String.valueOf(totalReports), "Total Reports",
                ContextCompat.getColor(this, R.color.colorPrimary));
        Log.d(TAG, "updateDashboard: Total reports card updated: " + totalReports);

        updateDashboardCard(resolvedReportsCard, String.valueOf(positiveReports), "Positive",
                ContextCompat.getColor(this, R.color.status_positive));
        Log.d(TAG, "updateDashboard: Positive reports card updated: " + positiveReports);


        updateDashboardCard(pendingReportsCard, String.valueOf(negativeReports), "Negative",
                ContextCompat.getColor(this, R.color.status_negative));
        Log.d(TAG, "updateDashboard: Negative reports card updated: " + negativeReports);


        inProgressReportsCard.setVisibility(View.GONE);
        Log.d(TAG, "updateDashboard: In progress card hidden");
    }

    private void updateDashboardCard(View cardView, String value, String label, int color) {
        Log.d(TAG, "updateDashboardCard: Updating card with label: " + label + ", value: " + value);
        TextView valueTextView = cardView.findViewById(R.id.card_value);
        TextView labelTextView = cardView.findViewById(R.id.card_label);

        valueTextView.setText(value);
        valueTextView.setTextColor(color);
        labelTextView.setText(label);
    }

    private void exportReport() {
        Log.d(TAG, "exportReport: Starting PDF export process");
        Toast.makeText(this, "Preparing report for export...", Toast.LENGTH_SHORT).show();

        try {
            // Create directory for reports if it doesn't exist
            File reportsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Report");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
                Log.d(TAG, "exportReport: Reports directory created: " + reportsDir.getAbsolutePath());
            } else {
                Log.d(TAG, "exportReport: Reports directory already exists: " + reportsDir.getAbsolutePath());
            }

            // Generate timestamped filename
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File pdfFile = new File(reportsDir, "ExportedReport_" + timeStamp + ".pdf");
            Log.d(TAG, "exportReport: PDF file path: " + pdfFile.getAbsolutePath());

            // Generate PDF file using PdfDocument API
            PdfDocument document = new PdfDocument();
            Log.d(TAG, "exportReport: PdfDocument created");

            // Page info (A4 size)
            int pageWidth = 595; // A4 width in points (72 points = 1 inch)
            int pageHeight = 842; // A4 height in points
            int margin = 50;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Log.d(TAG, "exportReport: First page started");

            // Draw content on the page
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(12);
            Log.d(TAG, "exportReport: Canvas and Paint initialized");

            // Draw header with blue background
            Paint headerBgPaint = new Paint();
            headerBgPaint.setColor(ContextCompat.getColor(this, R.color.colorPrimary));
            canvas.drawRect(0, 0, pageWidth, 80, headerBgPaint);

            // Draw "EXPORTED REPORT" title in header
            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.WHITE);
            titlePaint.setTextSize(24);
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Bacoor Connect - Exported Report", pageWidth / 2, 50, titlePaint);

            // Draw export date
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Exported on: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()),
                    pageWidth - margin, margin + 50, paint);
            Log.d(TAG, "exportReport: Header drawn");

            // Draw table headers
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            int y = margin + 100;
            int col1 = margin;
            int col2 = margin + 150;
            int col3 = margin + 300;
            int col4 = margin + 400;

            canvas.drawText("Category", col1, y, paint); // Updated header
            canvas.drawText("Location", col2, y, paint);
            canvas.drawText("Date", col3, y, paint);
            canvas.drawText("Status", col4, y, paint);
            Log.d(TAG, "exportReport: Table headers drawn");

            // Draw underline
            Paint linePaint = new Paint();
            linePaint.setColor(Color.BLACK);
            linePaint.setStrokeWidth(1);
            y += 5;
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);

            // Draw data rows
            y += 20;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            Log.d(TAG, "exportReport: Starting to draw data rows. Total items: " + reportItems.size());
            for (ReportItem item : reportItems) {

                if (item != null) {
                    // Updated log to use getCategory()
                    Log.d(TAG, "exportReport: Drawing row for item: Category=" + item.getCategory() + ", Location=" + item.getLocation());
                    // Updated to use getCategory()
                    canvas.drawText(item.getCategory(), col1, y, paint);
                    canvas.drawText(item.getLocation(), col2, y, paint);
                    // Updated log to use getCategory()
                    Log.d(TAG, "exportReport: Drawing row for item: Category=" + item.getCategory() + ", Location=" + item.getLocation());
                    // Updated to use getCategory()
                    canvas.drawText(item.getCategory(), col1, y, paint);
                    canvas.drawText(item.getLocation(), col2, y, paint);
                    canvas.drawText(item.getDate(), col3, y, paint);

                    // Determine status text and color based on upvotes vs downvotes for PDF
                    String statusText;
                    int statusColor;

                    if (item.getUpvotes() > item.getDownvotes()) {
                        statusText = "Positive";
                        statusColor = ContextCompat.getColor(this, R.color.status_positive);
                    } else if (item.getDownvotes() > item.getUpvotes()) {
                        statusText = "Negative";
                        statusColor = ContextCompat.getColor(this, R.color.status_negative);
                    } else {
                        statusText = "Neutral";
                        statusColor = Color.GRAY;
                    }

                    String reportDate = item.getDate();
                    Log.d(TAG, "exportReport: Item date: " + reportDate);
                    canvas.drawText(reportDate, col3, y, paint);

                    Paint statusPaint = new Paint();
                    statusPaint.setColor(statusColor);
                    statusPaint.setTextSize(12);
                    canvas.drawText(statusText, col4, y, statusPaint);
                    Log.d(TAG, "exportReport: Status drawn: " + statusText);


                    y += 25;

                    // Add a new page if needed
                    if (y > pageHeight - margin && reportItems.indexOf(item) < reportItems.size() - 1) {
                        document.finishPage(page);
                        Log.d(TAG, "exportReport: Finishing current page. Starting new page.");
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = margin + 50;
                        Log.d(TAG, "exportReport: New page started. Resetting y coordinate.");
                    }
                }
                else {
                    Log.w(TAG, "exportReport: Skipping null report item during PDF generation.");
                }
            }
            Log.d(TAG, "exportReport: Finished drawing data rows");

            // Draw footer with copyright notice
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(10);
            canvas.drawText("© 2023 Bacoor Connect - All Rights Reserved", pageWidth / 2, pageHeight - 30, paint);
            Log.d(TAG, "exportReport: Footer drawn");

            document.finishPage(page);
            Log.d(TAG, "exportReport: Final page finished");

            // Write to file
            FileOutputStream fos = new FileOutputStream(pdfFile);
            document.writeTo(fos);
            document.close();
            fos.close();
            Log.d(TAG, "exportReport: PDF written to file successfully");

            Toast.makeText(this, "Report ready for printing: " + pdfFile.getPath(), Toast.LENGTH_LONG).show();
            Log.d(TAG, "exportReport: PDF generated and saved at: " + pdfFile.getPath());

            // Share the file with printing apps (ACTION_VIEW is suitable for viewing/printing)
            Intent printIntent = new Intent(Intent.ACTION_VIEW);
            Uri fileUri = FileProvider.getUriForFile(this, getPackageName() + ".provider", pdfFile);
            printIntent.setDataAndType(fileUri, "application/pdf");
            printIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(TAG, "exportReport: Attempting to start print/view intent for URI: " + fileUri);
            startActivity(Intent.createChooser(printIntent, "Print Report"));
            Log.d(TAG, "exportReport: Intent chooser for printing started");


        } catch (Exception e) {
            Log.e(TAG, "exportReport: Failed to export report", e);
            e.printStackTrace();
            Toast.makeText(this, "Failed to print report: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    private void shareExportedFile(File file) {
        Log.d(TAG, "shareExportedFile: Preparing to share file: " + file.getAbsolutePath());
        Uri fileUri = FileProvider.getUriForFile(
                this,
                getApplicationContext().getPackageName() + ".provider",
                file
        );
        Log.d(TAG, "shareExportedFile: FileProvider URI: " + fileUri);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Log.d(TAG, "shareExportedFile: Starting share intent chooser");
        startActivity(Intent.createChooser(shareIntent, "Share Report"));
    }

    private void printReport() {
        Log.d(TAG, "printReport: Starting PDF printing process");
        Toast.makeText(this, "Preparing report for printing...", Toast.LENGTH_SHORT).show();

        try {
            // Create directory for reports if it doesn't exist
            File reportsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Report");
            if (!reportsDir.exists()) {
                reportsDir.mkdirs();
                Log.d(TAG, "printReport: Reports directory created: " + reportsDir.getAbsolutePath());
            } else {
                Log.d(TAG, "printReport: Reports directory already exists: " + reportsDir.getAbsolutePath());
            }


            // Generate timestamped filename
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File pdfFile = new File(reportsDir, "ReportHistory_" + timeStamp + ".pdf");
            Log.d(TAG, "printReport: PDF file path: " + pdfFile.getAbsolutePath());


            // Generate PDF file using PdfDocument API
            PdfDocument document = new PdfDocument();
            Log.d(TAG, "printReport: PdfDocument created");


            // Page info (A4 size)
            int pageWidth = 595; // A4 width in points (72 points = 1 inch)
            int pageHeight = 842; // A4 height in points
            int margin = 50;

            PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
            PdfDocument.Page page = document.startPage(pageInfo);
            Log.d(TAG, "printReport: First page started");

            // Draw content on the page
            Canvas canvas = page.getCanvas();
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setTextSize(12);
            Log.d(TAG, "printReport: Canvas and Paint initialized");

            // Draw header with blue background and logo
            Paint headerBgPaint = new Paint();
            headerBgPaint.setColor(ContextCompat.getColor(this, R.color.colorPrimary));
            canvas.drawRect(0, 0, pageWidth, 80, headerBgPaint);

            // Draw report title in header
            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.WHITE);
            titlePaint.setTextSize(24);
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText("Bacoor Connect - Report History", pageWidth / 2, 50, titlePaint);

            // Draw report date
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("Generated: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()),
                    pageWidth - margin, margin + 50, paint);
            Log.d(TAG, "printReport: Header drawn");

            // Draw table headers
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            int y = margin + 100;
            int col1 = margin;
            int col2 = margin + 150;
            int col3 = margin + 300;
            int col4 = margin + 400;

            canvas.drawText("Category", col1, y, paint); // Updated header
            canvas.drawText("Location", col2, y, paint);
            canvas.drawText("Date", col3, y, paint);
            canvas.drawText("Status", col4, y, paint);
            Log.d(TAG, "printReport: Table headers drawn");


            // Draw underline
            Paint linePaint = new Paint();
            linePaint.setColor(Color.BLACK);
            linePaint.setStrokeWidth(1);
            y += 5;
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint);

            // Draw data rows
            y += 20;
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
            Log.d(TAG, "printReport: Starting to draw data rows. Total items: " + reportItems.size());
            for (ReportItem item : reportItems) {
                if (item != null) {
                    // Updated log to use getCategory()
                    Log.d(TAG, "printReport: Drawing row for item: Category=" + item.getCategory() + ", Location=" + item.getLocation());

                    // Null check for Category (renamed from Type)
                    String reportCategory = item.getCategory();
                    if (reportCategory != null) {
                        canvas.drawText(reportCategory, col1, y, paint);
                    } else {
                        Log.w(TAG, "printReport: Item category is null for an item.");
                        canvas.drawText("N/A Category", col1, y, paint); // Updated text
                    }

                    // Null check for Location
                    String reportLocation = item.getLocation();
                    if (reportLocation != null) {
                        canvas.drawText(reportLocation, col2, y, paint);
                    } else {
                        Log.w(TAG, "printReport: Item location is null for an item.");
                        canvas.drawText("N/A Location", col2, y, paint);
                    }

                    // Null check for Date
                    String reportDate = item.getDate();
                    Log.d(TAG, "printReport: Item date before drawing: " + reportDate);
                    if (reportDate != null) {
                        canvas.drawText(reportDate, col3, y, paint);
                    } else {
                        // Updated log to use getCategory()
                        Log.e(TAG, "printReport: item.getDate() returned null for item (Category: " + item.getCategory() + ", Location: " + item.getLocation() + ")");
                        canvas.drawText("N/A Date", col3, y, paint);
                    }

                    // Determine status text and color
                    String statusText;
                    int statusColor;

                    if (item.getUpvotes() > item.getDownvotes()) {
                        statusText = "Positive";
                        statusColor = ContextCompat.getColor(this, R.color.status_positive);
                    } else if (item.getDownvotes() > item.getDownvotes()) {
                        statusText = "Negative";
                        statusColor = ContextCompat.getColor(this, R.color.status_negative);
                    } else {
                        statusText = "Neutral";
                        statusColor = Color.GRAY;
                    }

                    // Null check for Status Text
                    if (statusText != null) {
                        Paint statusPaint = new Paint();
                        statusPaint.setColor(statusColor);
                        statusPaint.setTextSize(12);
                        canvas.drawText(statusText, col4, y, statusPaint);
                        Log.d(TAG, "printReport: Status drawn: " + statusText);
                    } else {
                        Log.w(TAG, "printReport: Status text is null for an item.");
                        canvas.drawText("N/A Status", col4, y, paint);
                    }


                    y += 25;

                    // Add a new page if needed
                    if (y > pageHeight - margin && reportItems.indexOf(item) < reportItems.size() - 1) {
                        document.finishPage(page);
                        Log.d(TAG, "printReport: Finishing current page. Starting new page.");
                        pageInfo = new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, document.getPages().size() + 1).create();
                        page = document.startPage(pageInfo);
                        canvas = page.getCanvas();
                        y = margin + 50;
                        Log.d(TAG, "printReport: New page started. Resetting y coordinate.");
                    }
                } else {
                    Log.w(TAG, "printReport: Skipping null report item during PDF generation.");
                }
            }
            Log.d(TAG, "printReport: Finished drawing data rows");


            // Draw footer
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(10);
            canvas.drawText("© 2023 Bacoor Connect - All Rights Reserved", pageWidth / 2, pageHeight - 30, paint);
            Log.d(TAG, "printReport: Footer drawn");

            document.finishPage(page);
            Log.d(TAG, "printReport: Final page finished");


            // Write to file
            FileOutputStream fos = new FileOutputStream(pdfFile);
            document.writeTo(fos);
            document.close();
            fos.close();
            Log.d(TAG, "printReport: PDF written to file successfully");


            // Show preview
            Intent previewIntent = new Intent(this, PdfPreviewActivity.class);
            previewIntent.putExtra("PDF_FILE_PATH", pdfFile.getAbsolutePath());
            Log.d(TAG, "printReport: Starting PDF preview activity for path: " + pdfFile.getAbsolutePath());
            startActivity(previewIntent);


        } catch (Exception e) {
            Log.e(TAG, "printReport: Failed to generate PDF", e);
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }


    // Model class for report items
    public static class ReportItem {
        private String category; // Renamed from 'type'
        private String location;
        private String status;
        private String description;
        private String addressPrecision;
        private String imageUrl;
        private double latitude;
        private double longitude;
        private long timestamp;
        private int upvotes;
        private int downvotes;
        private String userId;

        // Updated constructor to match Firebase fields
        public ReportItem() {
            // Default constructor required for calls to DataSnapshot.getValue(ReportItem.class)
        }

        public ReportItem(String category, String location, String status, // Renamed parameter
                          String description, String addressPrecision, String imageUrl,
                          double latitude, double longitude, long timestamp,
                          int upvotes, int downvotes, String userId) {
            this.category = category; // Assign to the new field
            this.location = location;
            this.status = status;
            this.description = description;
            this.addressPrecision = addressPrecision;
            this.imageUrl = imageUrl;
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
            this.upvotes = upvotes;
            this.downvotes = downvotes;
            this.userId = userId;
        }

        // Getter for category (renamed from getType)
        public String getCategory() { return category; }
        public String getLocation() { return location; }

        // Modified getDate() to format the timestamp
        public String getDate() {
            if (timestamp > 0) { // Check if timestamp is valid
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            } else {
                return "N/A"; // Or some default value if timestamp is not set
            }
        }

        public String getStatus() { return status; }
        public String getDescription() { return description; }
        public String getAddressPrecision() { return addressPrecision; }
        public String getImageUrl() { return imageUrl; }
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public long getTimestamp() { return timestamp; }
        public int getUpvotes() { return upvotes; }
        public int getDownvotes() { return downvotes; }
        public String getUserId() { return userId; }
    }


    // Adapter for RecyclerView with modern design (now an inner class)
    private class ReportHistoryAdapter extends RecyclerView.Adapter<ReportHistoryAdapter.ViewHolder> {
        private List<ReportItem> reportItems;

        public ReportHistoryAdapter(List<ReportItem> reportItems) {
            this.reportItems = reportItems;
            Log.d(TAG, "ReportHistoryAdapter: Adapter initialized with " + reportItems.size() + " items");
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Log.d(TAG, "onCreateViewHolder: Creating new ViewHolder");
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_report, parent, false);
            return new ViewHolder(view);
        }
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReportItem item = reportItems.get(position);
            Log.d(TAG, "onBindViewHolder: Binding item at position " + position + ": Type=" + item.getCategory()); // Log binding position and item type

            // Set the category icon based on the category string
            int iconResId = getCategoryIconResource(item.getCategory());
            if (iconResId != 0) {
                holder.categoryIconImageView.setImageResource(iconResId);
                holder.categoryIconImageView.setVisibility(View.VISIBLE); // Make sure the ImageView is visible
            } else {
                holder.categoryIconImageView.setVisibility(View.GONE); // Hide the ImageView if no icon is found
            }

            // holder.typeTextView.setText(item.getCategory()); // Removed this line as per request
            holder.locationTextView.setText(item.getLocation());
            holder.dateTextView.setText(item.getDate()); // This now uses the formatted timestamp

            String statusText;
            int statusColor;

            if (item.getUpvotes() > item.getDownvotes()) {
                statusText = "Positive";
                statusColor = ContextCompat.getColor(ReportHistoryActivity.this, R.color.status_positive);
            } else if (item.getDownvotes() > item.getUpvotes()) {
                statusText = "Negative";
                statusColor = ContextCompat.getColor(ReportHistoryActivity.this, R.color.status_negative);
            } else {
                statusText = "Neutral";
                statusColor = Color.GRAY;
            }

            holder.statusTextView.setText(statusText);
            holder.statusTextView.setBackgroundTintList(ColorStateList.valueOf(statusColor));
            Log.d(TAG, "onBindViewHolder: Item status set to " + statusText + " with color " + Integer.toHexString(statusColor));


            // Click listener to open detail activity
            holder.itemView.setOnClickListener(v -> {
                Log.d(TAG, "onBindViewHolder: Item at position " + position + " clicked."); // Log item click
                // Pass the ReportItem directly or relevant data
                openReportDetail(item, false); // Pass the item
            });
        }

        @Override
        public int getItemCount() {
            return reportItems.size();
        }

        // ViewHolder class (now an inner class of the adapter)
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView typeTextView, locationTextView, dateTextView, statusTextView; // Kept typeTextView for now, but it's not being used
            ImageView categoryIconImageView; // Added ImageView field

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                typeTextView = itemView.findViewById(R.id.text_report_type);
                locationTextView = itemView.findViewById(R.id.text_report_location);
                dateTextView = itemView.findViewById(R.id.text_report_date);
                statusTextView = itemView.findViewById(R.id.text_report_status);
                categoryIconImageView = itemView.findViewById(R.id.category_icon_imageview);
            }
        }

        // Helper method to get the drawable resource ID based on category
        private int getCategoryIconResource(String category) {
            if (category == null) {
                return 0; // Return 0 or a default drawable if category is null
            }
            switch (category.toLowerCase(Locale.getDefault())) {
                case "fire":
                    return R.drawable.tag_fire;
                case "medical":
                    return R.drawable.tag_medical;
                case "road accident":
                    return R.drawable.tag_roadaccident;
                case "traffic":
                    return R.drawable.tag_traffic;
                default:
                    return 0;
            }
        }
    }

    // Method to open the detail activity
    void openReportDetail(ReportItem item, boolean includePhoto) {
        Log.d(TAG, "openReportDetail: Opening report detail for item: Type=" + item.getCategory()); // Log opening detail activity
        Intent intent = new Intent(ReportHistoryActivity.this, ReportDetailActivity.class);

        // Pass all relevant data from the ReportItem object
        intent.putExtra("report_type", item.getCategory());
        intent.putExtra("report_location", item.getLocation());
        intent.putExtra("report_date", item.getDate()); // Now using the formatted timestamp
        intent.putExtra("report_status", item.getStatus()); // Passing the original status field

        intent.putExtra("report_image_url", item.getImageUrl()); // Pass the image URL
        intent.putExtra("report_upvotes", item.getUpvotes());
        intent.putExtra("report_downvotes", item.getDownvotes());
        intent.putExtra("report_description", item.getDescription());
        intent.putExtra("report_latitude", item.getLatitude());
        intent.putExtra("report_longitude", item.getLongitude());
        intent.putExtra("report_timestamp", item.getTimestamp());
        intent.putExtra("report_userId", item.getUserId());


        // This flag seems related to local photo handling, keep it if needed
        intent.putExtra("include_photo", includePhoto);
        Log.d(TAG, "openReportDetail: Intent extras populated");

        startActivity(intent);
        Log.d(TAG, "openReportDetail: ReportDetailActivity started");
    }
}