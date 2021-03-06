package com.eniserkaya.gykchatapp;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int RC_SIGN_IN = 123;
    public static final int RC_PHOTO_PICKER = 12345;

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private FirebaseDatabase mFirebaseDatabase;
    private DatabaseReference mMessageFirebaseReference;

    private ChildEventListener mChildEventListener;

    private FirebaseAuth mFirebaseAuth;
    private FirebaseAuth.AuthStateListener mAuthStateListener;

    private FirebaseStorage mFirebaseStorage;
    private StorageReference mStorageReference;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Firebase baglantisi
        mFirebaseDatabase = FirebaseDatabase.getInstance();
        mFirebaseAuth = FirebaseAuth.getInstance();
        mFirebaseStorage = FirebaseStorage.getInstance();

        mMessageFirebaseReference = mFirebaseDatabase.getReference().child("messages"); // getReference("messages");
        mStorageReference = mFirebaseStorage.getReference().child("chat_photos");

        mAuthStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user!=null){
                    //Kullanıcı giriş yaptı
                }
                else{
                    // Kullanıcıya giriş yapma ekranını göster
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setProviders(
                                            Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        // view tanımlamaları
        mProgressBar = (ProgressBar)findViewById(R.id.progressBar);
        mMessageListView = (ListView)findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton)findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText)findViewById(R.id.messageEditText);
        mSendButton = (Button)findViewById(R.id.sendButton);
        // Adapter tanımlamaları
        List<MessageClass> messageList = new ArrayList<MessageClass>();
        mMessageAdapter = new MessageAdapter(this,R.layout.message_item,messageList);
        mMessageListView.setAdapter(mMessageAdapter);
        // progresbar ayarlamasi
        mProgressBar.setVisibility(View.INVISIBLE);

        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if(charSequence.toString().trim().length()>0)
                    mSendButton.setEnabled(true);
                else
                    mSendButton.setEnabled(false);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });

        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Galeri Açılacak
                // TODO: Fire an intent to show an image picker
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Seçim Yapınız."), RC_PHOTO_PICKER);
            }
        });

        mSendButton.setOnClickListener(this);
        mChildEventListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                MessageClass message = dataSnapshot.getValue(MessageClass.class);
                mMessageAdapter.add(message);
            }
            @Override
            public void onChildChanged(DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onChildRemoved(DataSnapshot dataSnapshot) {}
            @Override
            public void onChildMoved(DataSnapshot dataSnapshot, String s) {}
            @Override
            public void onCancelled(DatabaseError databaseError) {}
        };
        mMessageFirebaseReference.addChildEventListener(mChildEventListener);
    }
    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.sendButton:
                // Firebase'e data gonderecegiz.
                MessageClass message = new MessageClass(mFirebaseAuth.getCurrentUser().getDisplayName(),mMessageEditText.getText().toString().trim(),null);
                mMessageFirebaseReference.push().setValue(message);
                mMessageEditText.setText("");
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main,menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mFirebaseAuth.addAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mFirebaseAuth!=null)
            mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                // Giriş yaptı
                Toast.makeText(this, "Giriş Yapıldı", Toast.LENGTH_SHORT).show();
            }
            else if(resultCode == RESULT_CANCELED){
                finish();
            }
        }
        else if(requestCode == RC_PHOTO_PICKER){
            if(resultCode == RESULT_OK) {
                // Storage yolla
                Uri uri = data.getData();
                StorageReference mRefFoto = mStorageReference.child(uri.getLastPathSegment());
                mRefFoto.putFile(uri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                        Uri downloadUri = taskSnapshot.getDownloadUrl();
                        MessageClass messagePhoto = new MessageClass(mFirebaseAuth.getCurrentUser().getDisplayName(),
                                null,
                                downloadUri.toString());
                        mMessageFirebaseReference.push().setValue(messagePhoto);
                    }
                });
                //content : // asas // asas // 123412

            }


        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.sign_out_menu){
            // Cikis yapcak
            Toast.makeText(this, "Çıkış Yap", Toast.LENGTH_SHORT).show();
            FirebaseAuth.getInstance().signOut();
        }
        else if(item.getItemId() == R.id.profile_menu){
            Intent intent = new Intent(MainActivity.this,ProfileActivity.class);
            startActivity(intent);

        }

        return super.onOptionsItemSelected(item);
    }
}
