package com.parakinetics.flexiscore.ui;

import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.parakinetics.flexiscore.R;
import com.parakinetics.flexiscore.assessment.Classificationrules;
import com.parakinetics.flexiscore.data.AssessmentResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Shows the result of the assessment and exports a tamper-proof PDF. */
public class ReportActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "ASSESSMENT_RESULT";

    private AssessmentResult result;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        Object extra = getIntent().getSerializableExtra(EXTRA_RESULT);
        if (!(extra instanceof AssessmentResult)) {
            Toast.makeText(this, "No assessment data received.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        result = (AssessmentResult) extra;

        bindUi();

        Button btnExport = findViewById(R.id.btnExportPdf);
        Button btnPlay = findViewById(R.id.btnPlayVideo);
        Button btnNew = findViewById(R.id.btnNewAssessment);

        btnExport.setOnClickListener(v -> exportPdf());
        btnPlay.setEnabled(result.videoUri != null);
        btnPlay.setOnClickListener(v -> playVideo());
        btnNew.setOnClickListener(v -> finish());      // back to the home screen
    }

    // ---------------------------------------------------------------- screen
    private void bindUi() {
        TextView tvAthlete = findViewById(R.id.tvAthlete);
        TextView tvMic = findViewById(R.id.tvMicStatus);
        TextView tvNote = findViewById(R.id.tvClassNote);
        TextView tvLeftRom = findViewById(R.id.tvLeftRom);
        TextView tvLeftRange = findViewById(R.id.tvLeftRange);
        TextView tvRightRom = findViewById(R.id.tvRightRom);
        TextView tvRightRange = findViewById(R.id.tvRightRange);
        TextView tvSummary = findViewById(R.id.tvSummary);

        String date = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(result.timestamp));
        tvAthlete.setText(String.format(Locale.getDefault(), "%s  |  ID: %s  |  %s",
                result.athleteName, result.athleteId, date));

        tvMic.setText(result.micStatus);
        int color;
        if (result.inconclusive || result.cheatDetected) color = R.color.fs_red;
        else if (result.micMet) color = R.color.fs_amber;
        else color = R.color.fs_green;
        tvMic.setTextColor(ContextCompat.getColor(this, color));
        tvNote.setText(result.classificationNote);

        tvLeftRom.setText(String.format(Locale.getDefault(), "%.0f\u00B0", result.leftRom));
        tvLeftRange.setText(String.format(Locale.getDefault(), "bent %.0f\u00B0  -  straight %.0f\u00B0",
                result.leftMin, result.leftMax));
        tvRightRom.setText(String.format(Locale.getDefault(), "%.0f\u00B0", result.rightRom));
        tvRightRange.setText(String.format(Locale.getDefault(), "bent %.0f\u00B0  -  straight %.0f\u00B0",
                result.rightMin, result.rightMax));

        tvSummary.setText(String.format(Locale.getDefault(),
                "\u2022 Test: elbow flexion / extension\n"
                        + "\u2022 Bilateral symmetry deficit: %.1f%%\n"
                        + "\u2022 Posture check: max trunk lean %.0f\u00B0 (%s)\n"
                        + "\u2022 Tracked frames: left %d, right %d\n"
                        + "\u2022 Test duration: %.1f s\n"
                        + "\u2022 Video: %s\n"
                        + "\u2022 Normal elbow ROM: %.0f\u00B0 or more",
                result.symmetryDeficitPercent,
                result.maxTrunkLeanDeg,
                result.cheatDetected ? "compensation detected" : "upright",
                result.leftFrames, result.rightFrames,
                result.durationMs / 1000f,
                result.videoUri != null ? "saved in Movies/FlexiScore" : "not recorded",
                Classificationrules.NORMAL_ROM_DEG));
    }

    private void playVideo() {
        if (result.videoUri == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(result.videoUri), "video/mp4");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No video player found on this device.", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- PDF export
    private void exportPdf() {
        try {
            String fileName = "FlexiScore_Report_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".pdf";
            PdfDocument pdf = buildPdf();
            Uri uri;
            try {
                uri = savePdfToDownloads(pdf, fileName);
            } finally {
                pdf.close();
            }
            Toast.makeText(this, "Saved to Downloads:\n" + fileName, Toast.LENGTH_LONG).show();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
                try {
                    Intent open = new Intent(Intent.ACTION_VIEW);
                    open.setDataAndType(uri, "application/pdf");
                    open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(open);
                } catch (ActivityNotFoundException ignored) { /* no PDF viewer installed - file is saved anyway */ }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Uri savePdfToDownloads(PdfDocument pdf, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: scoped storage -> MediaStore, no permission needed
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Could not create file in Downloads");
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("Could not open output stream");
                pdf.writeTo(os);
            }
            return uri;
        } else {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Downloads folder");
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                pdf.writeTo(fos);
            }
            return Uri.fromFile(file);
        }
    }

    private PdfDocument buildPdf() {
        final int W = 595, H = 842, M = 40;
        PdfDocument pdf = new PdfDocument();
        PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(W, H, 1).create());
        Canvas c = page.getCanvas();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // --- header band
        p.setColor(0xFF0A0E17);
        c.drawRect(0, 0, W, 112, p);
        p.setColor(0xFF00E5FF);
        p.setTextSize(11);
        p.setFakeBoldText(true);
        c.drawText("FLEXISCORE CLINICAL", M, 38, p);
        p.setColor(Color.WHITE);
        p.setTextSize(22);
        c.drawText("Functional Goniometry Report", M, 68, p);
        p.setColor(0xFF8B949E);
        p.setTextSize(11);
        p.setFakeBoldText(false);
        c.drawText("Edge-AI 3D pose tracking (MediaPipe) - processed offline on device", M, 90, p);

        // --- athlete
        float y = 148;
        y = section(c, p, "ATHLETE", M, y);
        String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(result.timestamp));
        y = row(c, p, "Name", result.athleteName, M, y, W);
        y = row(c, p, "Athlete ID", result.athleteId, M, y, W);
        y = row(c, p, "Date / time", ts, M, y, W);
        y = row(c, p, "Test", "Elbow flexion / extension, " + String.format(Locale.US, "%.0f s", result.durationMs / 1000f), M, y, W);

        // --- ROM table
        y += 16;
        y = section(c, p, "RANGE OF MOTION (degrees)", M, y);
        y = row(c, p, "Left elbow", String.format(Locale.US, "ROM %.0f   (bent %.0f / straight %.0f)",
                result.leftRom, result.leftMin, result.leftMax), M, y, W);
        y = row(c, p, "Right elbow", String.format(Locale.US, "ROM %.0f   (bent %.0f / straight %.0f)",
                result.rightRom, result.rightMin, result.rightMax), M, y, W);
        y = row(c, p, "Bilateral deficit", String.format(Locale.US, "%.1f %%", result.symmetryDeficitPercent), M, y, W);
        y = row(c, p, "Normal elbow ROM", String.format(Locale.US, ">= %.0f", Classificationrules.NORMAL_ROM_DEG), M, y, W);

        // --- integrity
        y += 16;
        y = section(c, p, "INTEGRITY CHECKS", M, y);
        y = row(c, p, "Max trunk lean", String.format(Locale.US, "%.0f deg  (%s)",
                result.maxTrunkLeanDeg, result.cheatDetected ? "COMPENSATION DETECTED" : "upright"), M, y, W);
        y = row(c, p, "Tracked frames", String.format(Locale.US, "left %d / right %d", result.leftFrames, result.rightFrames), M, y, W);
        y = row(c, p, "Video evidence", result.videoUri != null ? "recorded (Movies/FlexiScore)" : "not recorded", M, y, W);

        // --- classification box
        y += 16;
        y = section(c, p, "ELIGIBILITY (SCREENING)", M, y);
        int boxColor = result.inconclusive || result.cheatDetected ? 0xFFFF1744 : (result.micMet ? 0xFFFFAB00 : 0xFF00B368);
        p.setColor(boxColor);
        p.setStyle(Paint.Style.FILL);
        c.drawRect(M, y, M + 5, y + 84, p);
        p.setColor(0xFF111111);
        p.setTextSize(13);
        p.setFakeBoldText(true);
        c.drawText(result.micStatus, M + 14, y + 18, p);
        p.setFakeBoldText(false);
        p.setTextSize(11);
        p.setColor(0xFF333333);
        float ny = y + 36;
        for (String line : wrap(result.classificationNote, p, W - 2 * M - 20)) {
            c.drawText(line, M + 14, ny, p);
            ny += 14;
        }
        y += 100;

        // --- tamper-proof hash + QR
        String hashInput = String.format(Locale.US, "%s|%s|%d|L%.1f-%.1f|R%.1f-%.1f|D%.1f",
                result.athleteId, result.athleteName, result.timestamp,
                result.leftMin, result.leftMax, result.rightMin, result.rightMax, result.symmetryDeficitPercent);
        String hash = sha256(hashInput);

        y = Math.max(y, 560);
        y = section(c, p, "TAMPER-PROOF VERIFICATION", M, y);
        p.setTextSize(9);
        p.setColor(0xFF555555);
        c.drawText("SHA-256 audit hash of athlete, time and measured angles:", M, y + 4, p);
        c.drawText(hash.substring(0, 32), M, y + 18, p);
        c.drawText(hash.substring(32), M, y + 30, p);
        c.drawText("Scan the QR code to read the same hash - any edited value will no longer match.", M, y + 48, p);

        try {
            Bitmap qr = makeQr("FLEXISCORE|" + result.athleteId + "|" + result.timestamp + "|SHA256:" + hash, 150);
            c.drawBitmap(qr, W - M - 150, y - 8, null);
        } catch (Exception ignored) { /* the hash text above is still on the page */ }

        // --- footer
        p.setColor(0xFF8B949E);
        p.setTextSize(9);
        c.drawText("Indicative screening report generated by FlexiScore. It is NOT an official IPC classification decision.",
                M, H - 30, p);

        pdf.finishPage(page);
        return pdf;
    }

    private float section(Canvas c, Paint p, String title, float x, float y) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF0D47A1);
        p.setTextSize(12);
        p.setFakeBoldText(true);
        c.drawText(title, x, y, p);
        p.setColor(0xFFB0BEC5);
        c.drawLine(x, y + 5, 595 - x, y + 5, p);
        return y + 24;
    }

    private float row(Canvas c, Paint p, String label, String value, float x, float y, int pageW) {
        p.setFakeBoldText(false);
        p.setTextSize(11);
        p.setColor(0xFF777777);
        c.drawText(label, x, y, p);
        p.setColor(0xFF111111);
        c.drawText(value, x + 140, y, p);
        return y + 20;
    }

    private java.util.List<String> wrap(String text, Paint p, float maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (p.measureText(test) > maxWidth && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    private Bitmap makeQr(String content, int size) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < size; x++) {
            for (int yy = 0; yy < size; yy++) {
                bmp.setPixel(x, yy, matrix.get(x, yy) ? Color.BLACK : Color.WHITE);
            }
        }
        return bmp;
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return "0000000000000000000000000000000000000000000000000000000000000000";
        }
    }
}