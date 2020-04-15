package client;

import com.clt.properties.Property;
import com.clt.speech.Language;
import com.clt.speech.SpeechException;
import com.clt.speech.recognition.AbstractRecognizer;
import com.clt.speech.recognition.Domain;
import com.clt.speech.recognition.RecognitionContext;
import com.clt.speech.recognition.RecognizerEvent;
import com.clt.speech.recognition.simpleresult.SimpleRecognizerResult;
import com.clt.srgf.Grammar;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;

import javax.sound.sampled.*;
import java.util.ArrayList;


public class GoogleRecognition extends AbstractRecognizer {

    // The language of the supplied audio. Even though additional languages are
    // provided by alternative_language_codes, a primary language is still required.
    String languageCode = "fr";

    // Specify up to 3 additional languages as possible alternative languages
    // of the supplied audio.
    String alternativeLanguageCodesElement = "es";
    String alternativeLanguageCodesElement2 = "en";


    public GoogleRecognition() {

    }


    @Override protected SimpleRecognizerResult startImpl() throws SpeechException {
        fireRecognizerEvent(RecognizerEvent.RECOGNIZER_LOADING);
        SimpleRecognizerResult result;
        fireRecognizerEvent(RecognizerEvent.RECOGNIZER_READY);
        result = new SimpleRecognizerResult(attemptRecognition());
        if (result != null) {
            fireRecognizerEvent(result);
            return result;
        } else {
            return null;
        }
    }


    @Override protected void stopImpl() {

    }

    @Override
    protected RecognitionContext createContext(String s, Grammar grammar, Domain domain, long l) throws SpeechException {
        return null;
    }

    @Override
    public RecognitionContext createTemporaryContext(Grammar grammar, Domain domain) throws SpeechException {
        return null;
    }

    /**
     * Performs microphone streaming speech recognition with a duration of 1 minute.
     * Code available at  https://cloud.google.com/speech-to-text/docs/streaming-recognize
     * @return most likely Result (no alternatives)
     */
    private String attemptRecognition(/**String languageCode, Optional<List<String>> alternativeLanguagesCodes**/) {
        //String stringResult = "";
        StringBuilder sb = new StringBuilder();
       // final SimpleRecognizerResult[] simpleResult = new SimpleRecognizerResult[1];
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
                                //response.getSpeechEventType() //can return END_OF_SINGLE_UTTERANCE if singe_utterance was set true in confif
                                StreamingRecognitionResult result = response.getResultsList().get(0); //opt: .getIsFinal() if temporary results set true
                                //optional: result.getStability // estimates (val between 0.00-1.00), if given result is likely to change/get optimized
                                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                                //System.out.printf("Transcript : %s\n", alternative.getTranscript());
                                sb.append(alternative.getTranscript());
                            }
                        }

                        public void onError(Throwable t) {
                            System.out.println("ERROR @RESPONSEOBSERVER: "+t);
                        }
                    };

            ClientStream<StreamingRecognizeRequest> clientStream =
                    client.streamingRecognizeCallable().splitCall(responseObserver);
            //configeration of recognizer
            RecognitionConfig recognitionConfig;
            recognitionConfig =
                   RecognitionConfig.newBuilder()
                           .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                           .setLanguageCode(languageCode)
                           //.addAllAlternativeLanguageCodes(alternativeLanguagesCodes) method name changed
                           .setSampleRateHertz(16000)

                           .build();
            //optional: setSingleUtterance(boolean val) // request will be stopped if no more language recognized (useful for commands)
            //optinal: setInterimResults() // returns temporary results that can be otimized later (after more audio is being processed)
            StreamingRecognitionConfig streamingRecognitionConfig =
                    StreamingRecognitionConfig.newBuilder().setConfig(recognitionConfig).build();

            StreamingRecognizeRequest request =
                    StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(streamingRecognitionConfig)
                            .build(); // The first request in a streaming call has to be a config, no audio

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
    public String[] transcribe(String s, Language language) throws SpeechException {
        return new String[0];
    }

    @Override
    public Property<?>[] getProperties() {
        return new Property[0];
    }

    @Override
    public Domain[] getDomains() throws SpeechException {
        return new Domain[0];
    }

    @Override
    public Domain createDomain(String s) throws SpeechException {
        return null;
    }

    @Override
    public void setDomain(Domain domain) throws SpeechException {

    }

    @Override
    public Domain getDomain() throws SpeechException {
        return null;
    }

    @Override
    public void setContext(RecognitionContext recognitionContext) throws SpeechException {

    }

    @Override
    public RecognitionContext getContext() throws SpeechException {
        return null;
    }
}
