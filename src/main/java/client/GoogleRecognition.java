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
    private String attemptRecognition() {
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
