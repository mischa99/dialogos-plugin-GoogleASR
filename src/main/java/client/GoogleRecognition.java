package client;

import com.clt.speech.Language;
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
import plugin.AbstractGoogleNode;

import javax.sound.sampled.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author mikhail
 * List of TODOs:
 *-control recognition duration with end_of_speech variable: true if SpeechRecognitionResponse in ResponseObserver contains
 * END_OF_SINGLE_UTTERANCE EventType. Only receivable if singleUtterance set to true in Configuration. Does not work, although set to true
 * - what to do if result confidance < set threshold (no match?, new message?)
 * - get all Languages available for Google's API
 */
public class GoogleRecognition extends GoogleBaseRecognizer {

    boolean stopping= false;

    boolean end_of_speech=false; //controlling speech recognition time in #attemptRecognition, ResponseObserver can change value if END_OF_SINGE_UTTERANCE Event received

    ResponseObserver<StreamingRecognizeResponse> responseObserver;
    //configuration of recognizer
    RecognitionConfig recognitionConfig;
    StreamingRecognitionConfig streamingRecognitionConfig;
    StreamingRecognizeRequest request; //transmit request to Google with this Object
    private StringBuilder sb = new StringBuilder(); //to create result in iterative manner
    String languageCode;

    public GoogleRecognition() {

    }


    @Override protected SimpleRecognizerResult startImpl() throws SpeechException {
        fireRecognizerEvent(RecognizerEvent.RECOGNIZER_LOADING);
        SimpleRecognizerResult result;

        Language l = new Language (AbstractGoogleNode.SELECTED_LANGUAGE.getName());
        if(l.getName() == "Deutsch")
            languageCode = "de-DE";
        if(l.getName() == "US English")
            languageCode ="en-US";
        if(l.getName() == "Español")
            languageCode="es-ES";
        if(l.getName() == "Français")
            languageCode="fr-FR";
        boolean iR = AbstractGoogleNode.SELECTED_INTERIM_RESULTS;
        boolean sU = AbstractGoogleNode.SELECTED_SINGLE_UTTERANCE;
        try {
            recognitionConfig = getRecognizerConfiguration(languageCode, 1);
            streamingRecognitionConfig = getStreamingConfiguration(iR, sU);
            responseObserver = createObserver();
        }
        catch (Exception e){
            e.printStackTrace();
            throw new SpeechException(e);
        }

        do {
            result = new SimpleRecognizerResult(attemptRecognition());
            if(result==null)
                break;
            //isMatch = isMatch(result);
        }while(result==null &!stopping && isActive());
        if (result != null && !stopping) {
            fireRecognizerEvent(result);
            return result;
        } else {
            return null;
        }

    }
    /**
     * Methods exists in Sphinx to regulate duration of recognition, need to figure out how to get the current grammar
    private boolean isMatch(SpeechResult speechResult) {
        Grammar gr = context.getGrammar();
        return gr.match(result, gr.getRoot()) != null;
    }
     */

    @Override protected void stopImpl() {
        stopping=true;
    }

    @Override
    protected RecognitionContext createContext(String s, Grammar grammar, Domain domain, long timestamp)  {
        Language l = new Language(AbstractGoogleNode.SELECTED_LANGUAGE.getName());
        return new RecognitionContext(s,domain,l,grammar);
    }


    @Override
    public RecognitionContext createTemporaryContext(Grammar grammar, Domain domain) throws SpeechException {
        return createContext("temp", grammar, domain, System.currentTimeMillis());
    }

    RecognitionConfig getRecognizerConfiguration(String languageCode, int maxAlternatives) {
        assert(maxAlternatives<=30 && maxAlternatives >= 0) : "you can only allow maxAlternatives to be between 0 and 30";

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
                        .setInterimResults(interimResult) // returns temporary results that can be optimized later (after more audio is being processed)
                        //if singleUtterance true then no InterimResults
                        .setSingleUtterance(singleUtterance) // request will be stopped if no more language recognized (useful for commands)
                        .build();

    return streamingRecognitionConfig;
    }

    ResponseObserver<StreamingRecognizeResponse> createObserver() {
        responseObserver =
                new ResponseObserver<StreamingRecognizeResponse>() {
                    ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

                    public void onStart(StreamController controller) {
                        fireRecognizerEvent(RecognizerEvent.RECOGNIZER_READY);
                    }

                    public void onResponse(StreamingRecognizeResponse response) {

                        StreamingRecognitionResult result = response.getResultsList().get(0);

                        if(result.getIsFinal()==false) {
                            SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);

                            SimpleRecognizerResult interimResult = new SimpleRecognizerResult(

                                            alternative.getTranscript()
                                            //",Stability: " +
                                            //+ result.getStability() // estimates (val between 0.00-1.00), if given result is likely to change/get optimized
                            );
                            fireRecognizerEvent(interimResult);

                        }
                        System.out.println("MR3.1 event int:" + response.getSpeechEventTypeValue());
                        if(response.getSpeechEventType() == StreamingRecognizeResponse.SpeechEventType.END_OF_SINGLE_UTTERANCE) {
                            System.out.println("MR3 event received");
                            end_of_speech = true;
                            responses.add(response);
                        }

                        responses.add(response);
                    }


                    public void onComplete() {
                                for (StreamingRecognizeResponse response : responses) {
                                    //if(response.getSpeechEventType() == StreamingRecognizeResponse.SpeechEventType.END_OF_SINGLE_UTTERANCE) //returns only if singleUtterance set true
                                    //  end_of_speech=true; //stop listening to micro
                                    StreamingRecognitionResult result = response.getResultsList().get(0);
                                    //optional: result.getStability // estimates (val between 0.00-1.00), if given result is likely to change/get optimized
                                    SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);

                                    if (result.getIsFinal() == true) {

                                        double threshold = alternative.getConfidence();

                                        if(threshold==0.0) //print result if no confidence available (0.0 is google's default val)
                                            sb.append(alternative.getTranscript());

                                        if (threshold >= AbstractGoogleNode.SELECTED_CONFIDENCE/100) {
                                            sb.append(alternative.getTranscript());
                                            System.out.printf("Final Result Confidence: %s\n", threshold);
                                       }
                                        else {
                                            JOptionPane.showMessageDialog(null,
                                                    "Results Confidence ("+ threshold+ ") was lower then the set threshold in options (" + AbstractGoogleNode.SELECTED_CONFIDENCE/100 + ")", "Low Confidence Result",
                                                    JOptionPane.ERROR_MESSAGE);
                                        }
                                    }
                                }
                           // }
                    }

                    public void onError(Throwable t) {
                        System.out.println("ERROR @GOOGLE_RESPONSE_OBSERVER.: "+ t);
                    }
                };
        return responseObserver;
    }

    /**
     * set Phrases (patterns declared in GUI window) that are likely to be recognized &
     * boost their recognition over other phrases (giving context to ASR) - use #patterns or #grammar from graph
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
     * Performs microphone streaming speech recognition with a duration of 5 seconds.
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
            while (end_of_speech==false) {
                long estimatedTime = System.currentTimeMillis() - startTime;
                byte[] data = new byte[6400];
                audio.read(data);
                if (estimatedTime > 5000 || end_of_speech==true) { // 5 seconds
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
