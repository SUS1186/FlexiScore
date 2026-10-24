package com.parakinetics.flexiscore.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.parakinetics.flexiscore.R;

/** Home screen: enter athlete details and start the assessment. */
public class Mainactivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EditText etName = findViewById(R.id.etName);
        EditText etId = findViewById(R.id.etId);
        Button btnStart = findViewById(R.id.btnStart);

        btnStart.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String id = etId.getText().toString().trim();
            if (name.isEmpty()) name = "Athlete";
            if (id.isEmpty()) id = "N/A";

            Intent intent = new Intent(this, AssessmentActivity.class);
            intent.putExtra(AssessmentActivity.EXTRA_ATHLETE_NAME, name);
            intent.putExtra(AssessmentActivity.EXTRA_ATHLETE_ID, id);
            startActivity(intent);
        });
    }
}