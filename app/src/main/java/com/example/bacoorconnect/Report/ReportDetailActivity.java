package com.example.bacoorconnect.Report;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bacoorconnect.R;

import java.util.ArrayList;
import java.util.List;

public class ReportDetailActivity extends AppCompatActivity {

    private RecyclerView rvReportHistory;
    private Button btnPrintReport, btnExportReport;
    private ImageView backButton;
    private TextView toolbarTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);

        // Initialize views
        rvReportHistory = findViewById(R.id.rvReportHistory);
        btnPrintReport = findViewById(R.id.btnPrintReport);
        btnExportReport = findViewById(R.id.btnExportReport);
        backButton = findViewById(R.id.back_button);

        // Set up back button click listener
        backButton.setOnClickListener(v -> {
            onBackPressed();
        });

        // Set title if passed from intent
        String title = getIntent().getStringExtra("title");
        if (title != null) {
            toolbarTitle.setText(title);
        }

        // Set up RecyclerView
        rvReportHistory.setLayoutManager(new LinearLayoutManager(this));
        List<ReportItem> reportItems = getDummyReports();
        ReportHistoryAdapter adapter = new ReportHistoryAdapter(reportItems);
        rvReportHistory.setAdapter(adapter);

        // Set button click listeners
        btnPrintReport.setOnClickListener(v -> {
            Toast.makeText(this, "Printing report...", Toast.LENGTH_SHORT).show();
            printReport();
        });

        btnExportReport.setOnClickListener(v -> {
            Toast.makeText(this, "Exporting report...", Toast.LENGTH_SHORT).show();
            exportReport();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void printReport() {
        Toast.makeText(this, "Print functionality is under development.", Toast.LENGTH_SHORT).show();
    }

    private void exportReport() {
        Toast.makeText(this, "Export functionality is under development.", Toast.LENGTH_SHORT).show();
    }

    private List<ReportItem> getDummyReports() {
        List<ReportItem> reports = new ArrayList<>();
        reports.add(new ReportItem("Type A", "Location 1", "2023-10-01", "Resolved"));
        reports.add(new ReportItem("Type B", "Location 2", "2023-10-02", "Pending"));
        return reports;
    }

    // Adapter for RecyclerView
    private class ReportHistoryAdapter extends RecyclerView.Adapter<ReportHistoryAdapter.ViewHolder> {
        private List<ReportItem> reportItems;

        public ReportHistoryAdapter(List<ReportItem> reportItems) {
            this.reportItems = reportItems;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_report, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReportItem item = reportItems.get(position);

            holder.typeTextView.setText(item.getType());
            holder.locationTextView.setText(item.getLocation());
            holder.dateTextView.setText(item.getDate());
            holder.statusTextView.setText(item.getStatus());

            // Set status background color
            int statusColor;
            switch(item.getStatus()) {
                case "Positive":
                    statusColor = ContextCompat.getColor(ReportDetailActivity.this, R.color.status_positive);
                    break;
                case "Negative":
                    statusColor = ContextCompat.getColor(ReportDetailActivity.this, R.color.status_negative);
                    break;
                default:
                    statusColor = ContextCompat.getColor(ReportDetailActivity.this, R.color.gray);
            }

            holder.statusTextView.setBackgroundColor(statusColor);
        }

        @Override
        public int getItemCount() {
            return reportItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView typeTextView, locationTextView, dateTextView, statusTextView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                typeTextView = itemView.findViewById(R.id.text_report_type);
                locationTextView = itemView.findViewById(R.id.text_report_location);
                dateTextView = itemView.findViewById(R.id.text_report_date);
                statusTextView = itemView.findViewById(R.id.text_report_status);
            }
        }
    }

    // ReportItem class stays the same
    public static class ReportItem {
        private final String type;
        private final String location;
        private final String date;
        private final String status;

        public ReportItem(String type, String location, String date, String status) {
            this.type = type;
            this.location = location;
            this.date = date;
            this.status = status;
        }

        public String getType() { return type; }
        public String getLocation() { return location; }
        public String getDate() { return date; }
        public String getStatus() { return status; }
    }
}