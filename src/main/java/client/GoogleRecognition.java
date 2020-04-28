package client;

import com.clt.speech.SpeechException;
import com.clt.speech.recognition.Domain;
import com.clt.speech.recognition.RecognitionContext;
import com.clt.speech.recognition.RecognizerEvent;
import com.clt.speech.recognition.simpleresult.SimpleRecognizerResult;
import com.clt.srgf.Grammar;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.cloud.speech.v1p1beta1.SpeechContext;
import com.google.protobuf.ByteString;

import javax.sound.sampled.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class GoogleRecognition extends GoogleBaseRecognizer {

    boolean stopping= false;

    // The language of the supplied audio. Even though additional languages are
    // provided by alternative_language_codes, a primary language is still required.
    String languageCode = "en-US";

    // Specify up to 3 additional languages as possible alternative languages
    // of the supplied audio.
    String alternativeLanguageCodesElement = "es";
    String alternativeLanguageCodesElement2 = "en";

    SpeechClient client;
    ResponseObserver<StreamingRecognizeResponse> responseObserver;
    ClientStream<StreamingRecognizeRequest> clientStream = null;
    //configuration of recognizer
    RecognitionConfig recognitionConfig;
    StreamingRecognitionConfig streamingRecognitionConfig;
    StreamingRecognizeRequest request; //transmit request to Google with this Object
    private StringBuilder sb = new StringBuilder(); //to create result in iterative manner


    public GoogleRecognition() {

    }


    @Override protected SimpleRecognizerResult startImpl() throws SpeechException {
        fireRecognizerEvent(RecognizerEvent.RECOGNIZER_LOADING);
        SimpleRecognizerResult result;
        recognitionConfig = getRecognizerConfiguration("en-US",1);
        streamingRecognitionConfig = getStreamingConfiguration(true,false);
        responseObserver = createObserver();

        do {
            fireRecognizerEvent(RecognizerEvent.RECOGNIZER_READY);
            result = new SimpleRecognizerResult(attemptRecognition());
            if(result==null)
                break;
            //isMatch = isMatch(result);
        }while(result==null &!stopping && isActive());
        if (result != null && !stopping) {
            fireRecognizerEvent(result);
            return result;
        } else {
            System.out.println("SimpleRecognizerResult returned null. Google probably did return null as result");
            return null;
        }

    }
    /**
     * Methods exists in Sphinx, need to figure out how to get the current grammar
    private boolean isMatch(SpeechResult speechResult) {
        Grammar gr = context.getGrammar();
        return gr.match(result, gr.getRoot()) != null;
    }
     */

    @Override protected void stopImpl() {
        stopping=true;
    }

    @Override
    protected RecognitionContext createContext(String s, Grammar grammar, Domain domain, long timestamp) throws SpeechException {
        return null;

    }

    @Override
    public RecognitionContext createTemporaryContext(Grammar grammar, Domain domain) throws SpeechException {
        return createContext("temp", grammar, domain, System.currentTimeMillis());
    }

    RecognitionConfig getRecognizerConfiguration(String languageCode, int maxAlternatives) {
        assert(maxAlternatives<=30 && maxAlternatives >= 0) : "you can only allow maxAlternatives to be between 0 and 30";
        /**
        if(maxAlternatives==null) {
            maxAlternatives=1;
        }
         */
        recognitionConfig =
                RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                        .setLanguageCode(languageCode)
                        //.addAllAlternativeLanguageCodes(alternativeLanguagesCodes)// only in beta version
                        .setSampleRateHertz(16000)
                        .setMaxAlternatives(maxAlternatives) //num of recognition hypothesis to be returned -> num of `SpeechRecognitionAlternative` messages within each `SpeechRecognitionResult`
                        .build();

        return recognitionConfig;
    }

    StreamingRecognitionConfig getStreamingConfiguration(boolean interimResult, boolean singleUtterance) {

        streamingRecognitionConfig =
                StreamingRecognitionConfig.newBuilder().setConfig(recognitionConfig)
                        .setInterimResults(interimResult) // returns temporary results that can be otimized later (after more audio is being processed)
                        //if singleUtterance true then no InterimResults?
                        .setSingleUtterance(singleUtterance) // request will be stopped if no more language recognized (useful for commands)
                        .build();

        return streamingRecognitionConfig;
    }

    ResponseObserver<StreamingRecognizeResponse> createObserver() {
        responseObserver =
                new ResponseObserver<StreamingRecognizeResponse>() {
                    ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

                    public void onStart(StreamController controller) {}

                    public void onResponse(StreamingRecognizeResponse response) {
                        StreamingRecognitionResult result = response.getResultsList().get(0);
                        if(result.getIsFinal()==false) {
                            SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                            System.out.printf("Interim Result : %s\n", alternative.getTranscript());
                            System.out.printf("Stability: %s\n", result.getStability()); //creates error message
                            //fireRecognizerEvent(RecognizerEvent.PARTIAL_RESULT,alternative);
                        }
                        responses.add(response);
                    }


                    public void onComplete() {
                        for (StreamingRecognizeResponse response : responses) {
                            //if(response.getSpeechEventType() == StreamingRecognizeResponse.SpeechEventType.END_OF_SINGLE_UTTERANCE ) { //can return END_OF_SINGLE_UTTERANCE if singe_utterance was set true in config
                            StreamingRecognitionResult result = response.getResultsList().get(0); //opt: .getIsFinal() if temporary results set true
                            //optional: result.getStability // estimates (val between 0.00-1.00), if given result is likely to change/get optimized
                            if (result.getIsFinal() == true) {

                                SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                                sb.append(alternative.getTranscript());
                                if(alternative.getConfidence() > 0.0){ //0.0 is default if no confidence available
                                    System.out.printf("Google's Final Result : %s\n", alternative.getTranscript());
                                    System.out.printf("Final Result Confidence: %s\n", alternative.getConfidence());
                                }
                            }
                        }
                    }

                    public void onError(Throwable t) {
                        System.out.println("ERROR @RESPONSEOBSERVER: "+ t);
                    }
                };
        return responseObserver;
    }

    /**
     * set Phrases (patterns declared in GUI window) that are likely to be recognized &
     * boost their recognition over other phrases (giving context to ASR)
     * @param phrase
     * @return
     */
    private List<SpeechContext> createGoogleSpeechContext (String[] phrase) {
        // Hint Boost. This value increases the probability that a specific
        // phrase will be recognized over other similar sounding phrases.
        // The higher the boost, the higher the chance of false positive
        // recognition as well. Can accept wide range of positive values.
        // Most use cases are best served with values between 0 and 20.
        // Using a binary search approach may help you find the optimal value.
        List<String> phrases = Arrays.asList(phrase);
        float boost = 20.0F;
        SpeechContext speechContextsElement =
                SpeechContext.newBuilder()
                        .addAllPhrases(phrases)
                        .setBoost(boost) //- only in v1p1beta1 version of api
                        .build();
        List<SpeechContext> speechContexts = Arrays.asList(speechContextsElement);

        return speechContexts;
    }

    /**
     * Performs microphone streaming speech recognition with a duration of 1 minute.
     * Code available at  https://cloud.google.com/speech-to-text/docs/streaming-recognize
     */
    public String attemptRecognition(){
        try (SpeechClient client = SpeechClient.create()) {

            ClientStream<StreamingRecognizeRequest> clientStream =
                    client.streamingRecognizeCallable().splitCall(responseObserver);

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
            fireRecognizerEvent(RecognizerEvent.START_OF_SPEECH);
            System.out.println("Start speaking");
            long startTime = System.currentTimeMillis();
            // Audio Input Stream
            AudioInputStream audio = new AudioInputStream(targetDataLine);
            while (true) {
                long estimatedTime = System.currentTimeMillis() - startTime;
                byte[] data = new byte[6400];
                audio.read(data);
                if (estimatedTime > 10000) { // 10 seconds
                    System.out.println("Stop speaking.");
                    targetDataLine.stop();
                    targetDataLine.close();
                    fireRecognizerEvent(RecognizerEvent.END_OF_SPEECH);
                    break;
                }

                request =
                        StreamingRecognizeRequest.newBuilder()
                                .setAudioContent(ByteString.copyFrom(data))
                                .build();
                clientStream.send(request);
            }
        }
            catch (Exception e) {
            System.out.println(e);
            }
        responseObserver.onComplete();
       // System.out.println("Googles result is:" + sb.toString());
        return sb.toString();
    }

    @Override
    public void setContext(RecognitionContext recognitionContext) throws SpeechException {

    }

    @Override
    public RecognitionContext getContext() throws SpeechException {
        return null;
    }
}
