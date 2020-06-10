package com.example.smartmusicplayer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.jean.jcplayer.model.JcAudio;
import com.example.jean.jcplayer.view.JcPlayerView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private boolean permission= false;
    Uri uri;
    String song_Name;
    String song_Url;
    ListView listView;
    ArrayList<String> arrayListSongName = new ArrayList<>();
    ArrayList<String> arrayListSongUrl = new ArrayList<>();
    ArrayAdapter<String> arrayAdapter;

    JcPlayerView jcPlayerView;
    ArrayList<JcAudio> jcAudios = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        listView = findViewById(R.id.mySongsListView);
        jcPlayerView = findViewById(R.id.jcplayer);

        retrieveSongs();
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                jcPlayerView.playAudio(jcAudios.get(position));
                jcPlayerView.setVisibility(View.VISIBLE);
                jcPlayerView.createNotification();
            }
        });
    }

    private void retrieveSongs() {
        DatabaseReference databaseReference =FirebaseDatabase.getInstance().getReference("Song");
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    songData songObj = ds.getValue(songData.class);
                    arrayListSongName.add(songObj.getSongName());
                    arrayListSongUrl.add(songObj.getSongUrl());
                    jcAudios.add(JcAudio.createFromURL(songObj.getSongName(),songObj.getSongUrl()));
                }
                arrayAdapter = new ArrayAdapter<String>(MainActivity.this,android.R.layout.simple_list_item_1,arrayListSongName){
                    @NonNull
                    @Override
                    //Customize the list View Not necessary
                    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                        View view = super.getView(position,convertView,parent);
                        TextView textView = (TextView)view.findViewById(android.R.id.text1);

                        textView.setSingleLine(true);
                        textView.setMaxLines(1);
                        return view;
                    }
                };
                jcPlayerView.initPlaylist(jcAudios,null);
                listView.setAdapter(arrayAdapter);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.my_menu,menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if(item.getItemId()==R.id.menu_btn_upload){
            if(checkPermissions()){
                selectSong();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private void selectSong()
    {
        Intent uploadIntent= new Intent();
        uploadIntent.setType("audio/*");
        uploadIntent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(uploadIntent,1)  ;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(requestCode==1)
        {
            if(resultCode==RESULT_OK){
                uri =data.getData();

                Cursor myCursor= getApplicationContext().getContentResolver().query(uri,null,null,null,null);
                int nameindex = myCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                myCursor.moveToFirst();
                song_Name=myCursor.getString(nameindex);
                myCursor.close();

                songUploadtoFirebaseStorage();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void songUploadtoFirebaseStorage(){

        StorageReference storageReference = FirebaseStorage.getInstance().getReference().child("songs")
                .child(uri.getLastPathSegment()); //use to save songs with unique names on database

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.show();

        storageReference.putFile(uri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                // To store information(url, name) of song on database
                Task<Uri> uriTask= taskSnapshot.getStorage().getDownloadUrl();
                while(!uriTask.isComplete());
                Uri urlSong = uriTask.getResult();
                song_Url = urlSong.toString();

                DatabaseDetailsUpload();
                progressDialog.dismiss();

            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,e.getMessage().toString(),Toast.LENGTH_SHORT).show();
                progressDialog.dismiss();
            }
        }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onProgress(@NonNull UploadTask.TaskSnapshot taskSnapshot) {
                //Formula to calculate upload percentage
                double progress = (100.0*taskSnapshot.getBytesTransferred())/taskSnapshot.getTotalByteCount();
                int currentProgress = (int)progress;
                //Display Progress
                progressDialog.setMessage("Uploaded: " + currentProgress + "%");
            }
        });

    }

    private void DatabaseDetailsUpload(){

        songData songObj = new songData(song_Name,song_Url);

        FirebaseDatabase.getInstance().getReference("Song").push().setValue(songObj)
                .addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if(task.isSuccessful())
                        {
                            Toast.makeText(MainActivity.this,"song Uploaded",Toast.LENGTH_SHORT);
                        }
                    }
                }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Toast.makeText(MainActivity.this,e.getMessage().toString(),Toast.LENGTH_SHORT);
            }
        });

    }

    private boolean checkPermissions()
    {
        Dexter.withContext(MainActivity.this).withPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse permissionGrantedResponse) {
                        permission=true;
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse permissionDeniedResponse) {
                        permission=false;
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permissionRequest, PermissionToken permissionToken) {
                        permissionToken.continuePermissionRequest();
                    }
                }).check();

        return permission;
    }
}
