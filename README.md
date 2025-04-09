
  NOTE:THIS   HOW TO PULL  THIS REPOSITORY   TO  YOUT ANDROID  STUDIO (PLEASE READ IT IS  IMPORTANT 
  
   GO  TO THE   HUMBURGET  MENU  FIND   FILES  --> NEW  -->  PROJECT  FROM VERSION CONTROLL

![image](https://github.com/user-attachments/assets/fce801d9-1d5f-4fe0-8e83-80b48b1eb40a)


PASTE YOUR  URL   :
 AND CLONE  IT

  ![image](https://github.com/user-attachments/assets/d9f64f32-0fa9-41e5-ada1-d1401b989557)
  



  

  AFTER  MAKE SURE  GRADLE(APP LEVEL TO  SYNC AND   IMPLEMENTATION IS COMPLETE
  
  ![image](https://github.com/user-attachments/assets/12ea4548-da45-4d8f-a2cc-2bd452bdcfc4)


  implementation("com.google.firebase:firebase-auth-ktx:22.1.2")
    implementation("com.google.firebase:firebase-firestore-ktx:25.1.2")
    implementation ("com.google.android.material:material:1.9.0")
    implementation ("com.google.guava:guava:30.1-android")

    // CameraX dependencies
    implementation ("androidx.camera:camera-camera2:1.0.0")
    implementation ("androidx.camera:camera-lifecycle:1.0.0")
    implementation ("androidx.camera:camera-view:1.0.0")
    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("com.google.android.material:material:1.10.0")

    //temporary
    implementation ("com.github.bumptech.glide:glide:4.12.0")
    implementation(libs.androidx.media3.common.ktx)
    annotationProcessor ("com.github.bumptech.glide:compiler:4.12.0")

    implementation ("androidx.appcompat:appcompat:1.4.0")
    implementation ("com.google.android.material:material:1.4.0")
    implementation  ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.4.0")
    implementation ("androidx.fragment:fragment-ktx:1.4.0")



    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.database.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.firebase.storage.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)






   GUIDE:

   TO CHANGE COLOR GO TO THE    APP -> VALUE->COLOR
   ![image](https://github.com/user-attachments/assets/87bb0318-d39f-4972-92e9-8bf66308eb01)

   EXAMPLE

    <color name="nav_item_active_color">#009688</color> <!-- Teal color for active items -->
    <color name="nav_item_inactive_color">#B0B0B0</color> <!-- Light grey for inactive icons -->


   MAKE SURE THE  ICON IS  INSERTED ON THE DRAWABLE   
   ![image](https://github.com/user-attachments/assets/4d4bf319-de78-466d-bd19-f8ec8b206abc)


   LET  ME TEACH  YOU HOW TO DOWNLOAD ICON   CLICK THE LINK:https://fonts.google.com/icons?icon.size=24&icon.color=%23000000&icon.platform=android&selected=Material+Symbols+Outlined:delete:FILL@0;wght@400;GRAD@0;opsz@24&icon.query=delete

   MAKE SURE   IT IS  ON  ANDROID AND  DOWNLOAD IT  DRAG  THE  FILE YOU  DOWNLOADED AND YOU CAN RENAME IT   FOR EXAMPLE   IC_ICON .XML
   ![image](https://github.com/user-attachments/assets/cbbd2d0b-f8cc-4261-a5f4-9ac6ef73d75a)



   IF WARNING IS OCCUR ON YOUR SDK  MAKE SURE YOU SET A COMPATIBLE SDK  TO YOU ANDROID STUDIO  ON  GRADLE APP LEVEL

  NOTE: IMPORTATION IS  REQUIRED  RIGHT CLICK THE ERROR  LINE  TO SEE  IF THERE IS  IMPORTATION NEEDED

  ![image](https://github.com/user-attachments/assets/108c4f66-36e4-47a4-abfc-409e0e67dd34)

   

android {
    namespace = "com.example.physiqueaiapkfinal"
    compileSdk = 35   // CHANGE  THIS BASED ON YOUR  COMPATIBLE SDK OR EMULATOR

    defaultConfig {
        applicationId = "com.example.physiqueaiapkfinal"
        minSdk = 29  // IF  THE MIN SDK IS  LOWER THNA   THE INPUT MAKE SURE TO  LOWER  IT TOO
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }








    

    
   

    
