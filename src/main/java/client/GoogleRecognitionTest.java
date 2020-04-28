package client;

import com.google.api.gax.paging.Page;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1.StreamingRecognizeResponse;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public class GoogleRecognitionTest {


    public final String jsonPath = "/Users/mikhail/Downloads/NLGwithFeedback-28befeaa51b4.json";
    ResponseObserver<StreamingRecognizeResponse> responseObserver;
    ClientStream<StreamingRecognizeRequest> clientStream = null;
    //configuration of recognizer
    RecognitionConfig recognitionConfig;
    StreamingRecognitionConfig streamingRecognitionConfig;

    @Before
    public void before() throws IOException {
        System.out.println("I am before method.");

        // You can specify a credential file by providing a path to GoogleCredentials.
        // Otherwise credentials are read from the GOOGLE_APPLICATION_CREDENTIALS environment variable.
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(jsonPath))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();

        System.out.println("Buckets:");
        Page<Bucket> buckets = storage.list();
        for (Bucket bucket : buckets.iterateAll()) {
            System.out.println(bucket.toString());
        }
    }

    @Test
    public void attemptRecognition() {
        System.out.println("Testing if GoogleASR returns a result");
        GoogleRecognition g = new GoogleRecognition();
        recognitionConfig = g.getRecognizerConfiguration("en-US",1);
        streamingRecognitionConfig = g.getStreamingConfiguration(true,false);
        responseObserver = g.createObserver();
        assertNotNull(g.attemptRecognition());
    }

    @After
    public void after() {
        System.out.println("I am after test method. I run after EVERY test");
    }



}