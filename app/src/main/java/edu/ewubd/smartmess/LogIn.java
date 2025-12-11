package edu.ewubd.smartmess;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class LogIn extends AppCompatActivity {

    EditText etUserId, etPassword;
    Button btnLogin;
    CheckBox cbRemUser, cbRemPass;

    FirebaseAuth mAuth;
    FirebaseFirestore db;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log_in);

        etUserId   = findViewById(R.id.etlogId);
        etPassword = findViewById(R.id.etLogpassword);
        btnLogin   = findViewById(R.id.btnsigngo);

        cbRemUser  = findViewById(R.id.checkboxlogremuser);
        cbRemPass  = findViewById(R.id.checkboxlogrempass);

        ImageView ivToggle = findViewById(R.id.ivPassLogin);

        mAuth  = FirebaseAuth.getInstance();
        db     = FirebaseFirestore.getInstance();
        prefs  = getSharedPreferences("login_prefs", MODE_PRIVATE);

        // Google / SignUp theke ashle email prefill
        String prefill = getIntent().getStringExtra("prefill_email");
        if (prefill != null && !prefill.isEmpty()) {
            etUserId.setText(prefill);
        }

        loadSavedLogin();

        // 👁 password toggle
        enablePasswordToggle(etPassword, ivToggle);

        btnLogin.setOnClickListener(v -> doLogin());
    }

    private void loadSavedLogin() {
        boolean rememberUser = prefs.getBoolean("remember_user", false);
        boolean rememberPass = prefs.getBoolean("remember_pass", false);

        if (rememberUser) {
            String savedEmail = prefs.getString("saved_email", "");
            if (etUserId.getText().toString().isEmpty()) {
                etUserId.setText(savedEmail);
            }
            cbRemUser.setChecked(true);
        }

        if (rememberPass) {
            String savedPass = prefs.getString("saved_password", "");
            etPassword.setText(savedPass);
            cbRemPass.setChecked(true);
        }
    }

    private void doLogin() {
        String email = etUserId.getText().toString().trim();
        String pass  = etPassword.getText().toString().trim();

        if (email.isEmpty() || pass.isEmpty()) {
            Toast.makeText(this,
                    "Email & Password required",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔸 Email validation (same as SignUp)
        if (!isValidEmail(email)) {
            Toast.makeText(this,
                    "Invalid email. Use lowercase, include '@', no spaces.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 1️⃣ প্রথমে চেষ্টা করবো normal login
        mAuth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    saveRememberSettings(email, pass);
                    checkUserInFirestore(user);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) {
                        // ❌ এই email দিয়ে কোনো user নেই → এখান থেকেই NEW account create করবো
                        Toast.makeText(this,
                                "No account found. Creating new account...",
                                Toast.LENGTH_SHORT).show();

                        createAccountFromLogin(email, pass);

                    } else if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                        // 🔐 user ache, but password ভুল
                        Toast.makeText(this,
                                "Wrong password. Please try again.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this,
                                "Login failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void createAccountFromLogin(String email, String pass) {
        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        Toast.makeText(this,
                                "User creation failed",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = user.getUid();

                    // ekhane login screen theke aschi, extra userId nei;
                    // সহজভাবে userId hisebe email use kore dilam
                    String userId  = email;
                    String contact = "";

                    java.util.Map<String, Object> data = new java.util.HashMap<>();
                    data.put("userId", userId);
                    data.put("email", email);
                    data.put("contact", contact);
                    data.put("role", "member");

                    FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .set(data)
                            .addOnSuccessListener(v -> {
                                Toast.makeText(this,
                                        "New account created & logged in",
                                        Toast.LENGTH_SHORT).show();
                                saveRememberSettings(email, pass);
                                goToDashboardByRole("member");
                            })
                            .addOnFailureListener(err ->
                                    Toast.makeText(this,
                                            "Profile save failed: " + err.getMessage(),
                                            Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(err ->
                        Toast.makeText(this,
                                "Account create failed: " + err.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }


    private boolean isValidEmail(String email) {
        if (!email.contains("@")) return false;
        if (email.contains(" ")) return false;
        if (!email.equals(email.toLowerCase())) return false;
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void saveRememberSettings(String email, String pass) {
        SharedPreferences.Editor editor = prefs.edit();

        if (cbRemUser.isChecked()) {
            editor.putBoolean("remember_user", true);
            editor.putString("saved_email", email);
        } else {
            editor.putBoolean("remember_user", false);
            editor.remove("saved_email");
        }

        if (cbRemPass.isChecked()) {
            editor.putBoolean("remember_pass", true);
            editor.putString("saved_password", pass);
        } else {
            editor.putBoolean("remember_pass", false);
            editor.remove("saved_password");
        }

        editor.apply();
    }

    private void checkUserInFirestore(FirebaseUser user) {
        if (user == null) {
            startActivity(new Intent(this, PreSignUp.class));
            finish();
            return;
        }

        String uid = user.getUid();
        DocumentReference ref = db.collection("users").document(uid);

        ref.get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists()) {
                        String role = doc.getString("role");
                        if (role == null) role = "member";
                        goToDashboardByRole(role);
                    } else {
                        Intent i = new Intent(this, SignUp.class);
                        i.putExtra("email", user.getEmail());
                        i.putExtra("name", user.getDisplayName());
                        startActivity(i);
                        finish();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load user info: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    private void goToDashboardByRole(String role) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("role", role);
        startActivity(i);
        finish();
    }

    private void enablePasswordToggle(EditText editText, ImageView icon) {

        icon.setOnClickListener(v -> {

            int currentType = editText.getInputType();

            if (currentType ==
                    (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {

                // Show password
                editText.setInputType(InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

                icon.setImageResource(R.drawable.ic_visiblity);

            } else {
                // Hide password
                editText.setInputType(InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD);

                icon.setImageResource(R.drawable.ic_visiblility_off);
            }

            editText.setSelection(editText.getText().length());
        });
    }

}
