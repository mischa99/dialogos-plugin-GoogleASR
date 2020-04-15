package plugin;

import com.clt.diamant.Device;
import com.clt.diamant.InputCenter;
import com.clt.diamant.graph.nodes.AbstractInputNode;
import com.clt.diamant.graph.nodes.NodeExecutionException;
import com.clt.script.exp.Pattern;
import com.clt.script.exp.patterns.VarPattern;
import com.clt.speech.recognition.LanguageName;
import com.clt.speech.recognition.MatchResult;
import com.clt.srgf.Grammar;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import org.jsoup.Jsoup;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
//Import the Google Cloud client library


/**
 * Created by Mikhail on 17.02.20.
 */
public class SpeechToTextNode extends AbstractInputNode {



    public SpeechToTextNode() {

    }



    @Override
    public GoogleRecognitionExecutor createRecognitionExecutor(Grammar recGrammar) {
        /**
         recGrammar.requestRobustness(Boolean.TRUE == getProperty(ENABLE_GARBAGE));
         return new GoogleRecognitionExecutor();
    **/
        return null;
    }


    @Override
    public Device getDevice() {
        return null;
    }


    @Override
    public MatchResult graphicallyRecognize(JLayeredPane layer, com.clt.srgf.Grammar recGrammar, Pattern[] patterns, long timeout, float confidenceThreshold, boolean interactiveTest) {
        MatchResult match = null;
        int trials = -1;
        String result;
        do {
            result = attemptRecognition();
            match = findMatch(result, recGrammar, patterns);
            trials++;
        } while (match == null && trials < timeout);
        if (trials > timeout)
            System.out.println("reached timeout: " + timeout);
        if (interactiveTest)
            System.out.println("confirming result: " + match.getUtterance());
        return match;
    }


    @Override
    public AudioFormat getAudioFormat() {
        return null;
    }

    /**
     * Performs microphone streaming speech recognition with a duration of 1 minute.
     * Code available at  https://cloud.google.com/speech-to-text/docs/streaming-recognize
     * @return most likely Result (no alternatives)
     */
    private String attemptRecognition() {
        StringBuilder sb = new StringBuilder(); //create Recognition Result String
        ResponseObserver<StreamingRecognizeResponse> responseObserver = null;
        try (SpeechClient client = SpeechClient.create()) {

            responseObserver =
                    new ResponseObserver<StreamingRecognizeResponse>() {
                        ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

                        public void onStart(StreamController controller) {}

                        public void onResponse(StreamingRecognizeResponse response) {
                            responses.add(response);
                        }

                        public void onComplete() {
                            for (StreamingRecognizeResponse response : responses) {
                                StreamingRecognitionResult result = response.getResultsList().get(0);
                                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                                //System.out.printf("Transcript : %s\n", alternative.getTranscript());
                                //stringResult = alternative.getTranscript();
                                sb.append(alternative.getTranscript());
                            }
                            //simpleResult[0] = new SimpleRecognizerResult(stringResult);
                            //return simpleResult[0];
                        }

                        public void onError(Throwable t) {
                            System.out.println("ERROR @RESPONSEOBSERVER: "+t);
                        }
                    };

            ClientStream<StreamingRecognizeRequest> clientStream =
                    client.streamingRecognizeCallable().splitCall(responseObserver);

            RecognitionConfig recognitionConfig =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            .setLanguageCode("en-US")
                            .setSampleRateHertz(16000)
                            .build();
            StreamingRecognitionConfig streamingRecognitionConfig =
                    StreamingRecognitionConfig.newBuilder().setConfig(recognitionConfig).build();

            StreamingRecognizeRequest request =
                    StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(streamingRecognitionConfig)
                            .build(); // The first request in a streaming call has to be a config

            clientStream.send(request);
            // SampleRate:16000Hz, SampleSizeInBits: 16, Number of channels: 1, Signed: true,
            // bigEndian: false
            AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
            DataLine.Info targetInfo =
                    new DataLine.Info(
                            TargetDataLine.class,
                            audioFormat); // Set the system information to read from the microphone audio stream

            if (!AudioSystem.isLineSupported(targetInfo)) {
                System.out.println("Microphone not supported");
                System.exit(0);
            }
            // Target data line captures the audio stream the microphone produces.
            TargetDataLine targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
            targetDataLine.open(audioFormat);
            targetDataLine.start();
            System.out.println("Start speaking");
            long startTime = System.currentTimeMillis();
            // Audio Input Stream
            AudioInputStream audio = new AudioInputStream(targetDataLine);
            while (true) {
                long estimatedTime = System.currentTimeMillis() - startTime;
                byte[] data = new byte[6400];
                audio.read(data);
                if (estimatedTime > 60000) { // 60 seconds
                    System.out.println("Stop speaking.");
                    targetDataLine.stop();
                    targetDataLine.close();
                    break;
                }
                request =
                        StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(data))
                                .build();
                clientStream.send(request);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        responseObserver.onComplete();
        return sb.toString();
    }

    @Override
    public void recognizeInBackground(Grammar recGrammar, InputCenter input, VarPattern backgroundPattern, float confidenceThreshold) {
        throw new NodeExecutionException(this, "TextInputNode does not support background recognition");
    }

    private LanguageName defaultLanguage = new LanguageName("", null);

    @Override
    public List<LanguageName> getAvailableLanguages() {
        org.jsoup.nodes.Document doc = null;
        ArrayList<LanguageName> list = new ArrayList<LanguageName>(Arrays.asList(defaultLanguage));
        try {
            doc = Jsoup.connect("https://cloud.google.com/speech-to-text/docs/languages").get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        org.jsoup.select.Elements rows = doc.select("tr");
        for(org.jsoup.nodes.Element row :rows)
        {
            org.jsoup.select.Elements columns = row.select("td");
            for (org.jsoup.nodes.Element column:columns)
            {
                System.out.print(column.text());
                list.add(new LanguageName("", column.text()));
            }
            System.out.println();
        }
        return list;
    }

    @Override
    public LanguageName getDefaultLanguage() {
        return defaultLanguage;
    }
   // private static Device googleDevice = new Device(Resources.getString("Google"));


}




