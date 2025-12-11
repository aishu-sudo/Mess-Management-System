package edu.ewubd.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LinearLayout MealD, MemberList, ExpenseL, MealH, CalculationB;
    private LinearLayout recentact;
    private TextView tvrecent;
    private Button btnLogout;
    private TextView todaymeal, totalex, totalmem;

    private FirebaseAuth mAuth;
    private DatabaseReference rdb;

    // safe flag to enable persistence only once (avoid IllegalStateException)
    private static boolean persistenceEnabled = false;

    // role extra (এখন কাজে লাগাচ্ছো না, চাইলে মুছে ফেলতে পারো)
    private String userRole = "member";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);

        // enable disk persistence once
        if (!persistenceEnabled) {
            try {
                FirebaseDatabase.getInstance().setPersistenceEnabled(true);
            } catch (Exception e) {
                // already enabled / ignore
            }
            persistenceEnabled = true;
        }

        // firebase init
        mAuth = FirebaseAuth.getInstance();
        rdb = FirebaseDatabase.getInstance().getReference();

        // view binds
        MealD = findViewById(R.id.MealD);
        MemberList = findViewById(R.id.MemberList);
        ExpenseL = findViewById(R.id.ExpenseL);
        MealH = findViewById(R.id.MealH);
        CalculationB = findViewById(R.id.CalculationB);

        recentact = findViewById(R.id.recentact);
        btnLogout = findViewById(R.id.btnLogout);
        tvrecent = findViewById(R.id.tvrecent);

        todaymeal = findViewById(R.id.todaymeal);
        totalex = findViewById(R.id.totalex);
        totalmem = findViewById(R.id.totalmem);

        // optional: role from intent
        String roleExtra = getIntent().getStringExtra("role");
        if (roleExtra != null) {
            userRole = roleExtra;
        }

        // default hide recent + logout (login না থাকলে)
        recentact.setVisibility(View.GONE);
        tvrecent.setVisibility(View.GONE);
        btnLogout.setVisibility(View.GONE);

        // সব click listener সেট করি
        setClickListeners();
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkUserAndRole();   // লগ ইন আছেকি দেখে recent + logout দেখাই/লুকাই
        loadSummaryValues();
    }

    // ---------- CLICK LISTENERS ----------

    private final View.OnClickListener quickAction = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            FirebaseUser user = mAuth.getCurrentUser();

            if (user == null) {
                Toast.makeText(MainActivity.this,
                        "Please log in first", Toast.LENGTH_SHORT).show();

                Intent i = new Intent(MainActivity.this, PreSignUp.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();   // MainActivity ke stack theke ber kore dao
                return;
            }

            int id = v.getId();
            if (id == R.id.MealD) {
                startActivity(new Intent(MainActivity.this, Meal_Dashboard.class));
            } else if (id == R.id.MemberList) {
                startActivity(new Intent(MainActivity.this, Member_List.class));
            } else if (id == R.id.ExpenseL) {
                startActivity(new Intent(MainActivity.this, Expense_List.class));
            } else if (id == R.id.MealH) {
                startActivity(new Intent(MainActivity.this, Meal_History.class));
            }
        }
    };

    private void setClickListeners() {
        // চারটা কার্ডেই একই quickAction ব্যবহার
        MealD.setOnClickListener(quickAction);
        MemberList.setOnClickListener(quickAction);
        ExpenseL.setOnClickListener(quickAction);
        MealH.setOnClickListener(quickAction);
        // CalculationB চাইলে পরে add করবে

        // 🔴 LOGOUT: Firebase + Google sign out + PreSignUp এ যাওয়া
        btnLogout.setOnClickListener(view -> {
            // 1) Firebase sign out
            mAuth.signOut();

            // 2) Google sign out (so next time chooser appears)
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .build();

            GoogleSignInClient gClient = GoogleSignIn.getClient(MainActivity.this, gso);
            gClient.signOut(); // async, no need to wait

            // 3) move to PreSignUp screen
            Intent intent = new Intent(MainActivity.this, PreSignUp.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            Toast.makeText(MainActivity.this, "Logged out", Toast.LENGTH_SHORT).show();
            finishAffinity();
        });
    }

    // ---------- USER / ROLE UI CONTROL ----------

    private void checkUserAndRole() {
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            // ❌ কোনো user লগ ইন নেই
            recentact.setVisibility(View.GONE);
            tvrecent.setVisibility(View.GONE);
            btnLogout.setVisibility(View.GONE);
        } else {
            // ✅ যেকোনো logged-in user (admin/member)
            recentact.setVisibility(View.VISIBLE);
            tvrecent.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.VISIBLE);
        }
    }

    // ---------- SUMMARY VALUES (Realtime DB) ----------

    private void loadSummaryValues() {
        if (mAuth.getCurrentUser() == null) return;

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        loadTodayMealCount(today);
        loadTotalExpenses();
        loadMemberCount();
    }

    private void loadTodayMealCount(String today) {
        rdb.child("meals").child(today).child("mealCount").get()
                .addOnSuccessListener(snap -> {
                    long count = snap.getValue(Long.class) == null ? 0 : snap.getValue(Long.class);
                    todaymeal.setText(String.valueOf(count));
                })
                .addOnFailureListener(e -> todaymeal.setText("0"));
    }

    private void loadTotalExpenses() {
        rdb.child("expenses").get()
                .addOnSuccessListener(snap -> {
                    long total = 0;

                    for (DataSnapshot d : snap.getChildren()) {
                        Object amount = d.child("amount").getValue();

                        if (amount instanceof Number) {
                            total += ((Number) amount).longValue();
                        }
                    }

                    totalex.setText("TK. " + total);
                })
                .addOnFailureListener(e -> totalex.setText("TK. 0"));
    }

    private void loadMemberCount() {
        rdb.child("users")
                .orderByChild("role")
                .equalTo("member")
                .get()
                .addOnSuccessListener(snap -> {
                    totalmem.setText(String.valueOf(snap.getChildrenCount()));
                })
                .addOnFailureListener(e -> totalmem.setText("0"));
    }
}
