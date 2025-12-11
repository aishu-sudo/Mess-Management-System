package edu.ewubd.smartmess;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUp extends AppCompatActivity {

    EditText etUserId, etEmail, etContact, etPassword, etConfirm;
    Button btnSignUp;

    FirebaseAuth mAuth;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        // 🔹 IDs must match XML
        etUserId   = findViewById(R.id.etUserId);
        etEmail    = findViewById(R.id.etsignEmail);
        etContact  = findViewById(R.id.etsignContact);
        etPassword = findViewById(R.id.etsignPassword);
        etConfirm  = findViewById(R.id.etUsersignpassword);
        btnSignUp  = findViewById(R.id.btnsigngo);

        // password eye icons
        ImageView ivPass = findViewById(R.id.ivPassSignup);
        ImageView ivConfirm = findViewById(R.id.ivPassSignupConfirm);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        // 👁 password show/hide
        enablePasswordToggle(etPassword, ivPass);
        enablePasswordToggle(etConfirm, ivConfirm);

        // Google / PreSignUp theke ashle email & name prefill
        String emailExtra = getIntent().getStringExtra("email");
        String nameExtra  = getIntent().getStringExtra("name");
        if (emailExtra != null) {
            etEmail.setText(emailExtra);
            etEmail.setEnabled(false);   // Google email change korte dibe na
        }

        btnSignUp.setOnClickListener(v -> doSignUp(nameExtra));
    }

    private void doSignUp(String nameExtra) {
        String userId  = etUserId.getText().toString().trim();
        String email   = etEmail.getText().toString().trim();
        String contact = etContact.getText().toString().trim();
        String pass    = etPassword.getText().toString().trim();
        String confirm = etConfirm.getText().toString().trim();

        // 🔸 Basic check
        if (userId.isEmpty() || email.isEmpty()) {
            Toast.makeText(this,
                    "User ID & Email required",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔸 Email validation: must contain @, no space, all lowercase
        if (!isValidEmail(email)) {
            Toast.makeText(this,
                    "Invalid email. Use lowercase, include '@', no spaces.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // 🔸 Password strength check (example: min 6 char)
        if (!isValidPassword(pass)) {
            Toast.makeText(this,
                    "Password must be at least 6 characters.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 🔸 Password & Confirm match check
        if (!pass.equals(confirm)) {
            Toast.makeText(this,
                    "Password & Confirm Password do not match.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        Toast.makeText(this,
                                "User not created properly",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String uid = user.getUid();
                    saveUserToFirestore(uid, userId, email, contact, "member", nameExtra);
                })
                .addOnFailureListener(e -> {
                    if (e instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                        // already exists → Login e pathai
                        Toast.makeText(this,
                                "Account already exists, please log in.",
                                Toast.LENGTH_SHORT).show();

                        Intent i = new Intent(this, LogIn.class);
                        i.putExtra("prefill_email", email);
                        startActivity(i);
                        finish();
                    } else {
                        Toast.makeText(this,
                                "Sign up failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidEmail(String email) {
        // must have '@'
        if (!email.contains("@")) return false;
        // no spaces
        if (email.contains(" ")) return false;
        // all lowercase
        if (!email.equals(email.toLowerCase())) return false;
        // optional: android built-in pattern
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private boolean isValidPassword(String pass) {
        // ekhane chaile aro rule add korte paro (digit, special char, etc.)
        return pass.length() >= 6;
    }

    private void saveUserToFirestore(String uid, String userId, String email,
                                     String contact, String role, String nameExtra) {

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("email", email);
        data.put("contact", contact);
        data.put("role", role);
        if (nameExtra != null) {
            data.put("name", nameExtra);
        }

        db.collection("users").document(uid).set(data)
                .addOnSuccessListener(v -> {
                    Toast.makeText(this,
                            "Sign up success",
                            Toast.LENGTH_SHORT).show();
                    goToDashboardByRole(role);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to save profile: " + e.getMessage(),
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
                editText.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);

                icon.setImageResource(R.drawable.ic_visiblity);   // 👁 open eye

            } else {
                // Hide password
                editText.setInputType(
                        InputType.TYPE_CLASS_TEXT |
                                InputType.TYPE_TEXT_VARIATION_PASSWORD);

                icon.setImageResource(R.drawable.ic_visiblility_off);  // 👁‍🗨 closed eye
            }

            editText.setSelection(editText.getText().length());
        });
    }
}
