package plugin;

import com.clt.diamant.graph.nodes.AbstractInputNode;
import com.clt.script.exp.Pattern;
import com.clt.speech.recognition.*;
import com.clt.srgf.Grammar;

import java.util.concurrent.*;

public class GoogleRecognitionExecutor implements RecognitionExecutor {

    private AbstractRecognizer recognizer;

    public GoogleRecognitionExecutor(AbstractRecognizer recognizer) {
        this.recognizer = recognizer;
    }

    public GoogleRecognitionExecutor() {

    }

    public MatchResult start(final Grammar grammar, final Pattern[] patterns, long timeout, final RecognizerListener stateListener, final float recognitionThreshold)
            throws InterruptedException, ExecutionException, TimeoutException {
        // works by submitting a Future job and waiting for it to return (forever or until the timeout is reached)
        // in the case of a timeout, stop() will be called externally.

        Future<MatchResult> result = Executors.newSingleThreadExecutor().submit(() -> {
            // all that is relevant to context, thus including the recognition threshold!
            recognizer.setContext(grammar);

            if (stateListener != null) {
                recognizer.addRecognizerListener(stateListener);
            }
            RecognitionResult recognitionResult;
            try {
                 recognitionResult = recognizer.startLiveRecognition(); // this AbstractRecognizer Method stars the #startImpl Method of "this" object
                if (recognitionResult == null) {
                    return null;
                }
            } finally {
                if (stateListener != null) {
                    recognizer.removeRecognizerListener(stateListener);
                }
            }
            Utterance utterance = recognitionResult.getAlternative(0);
            MatchResult mr = AbstractInputNode.findMatch(utterance.getWords(), grammar, patterns);
            // TODO unlocalized string
            if (mr == null)
                throw new RecognizerException("No match for recognition result '" + utterance.getWords() + "'");
            return mr;
        });

        if (timeout <= 0) {
            return result.get();
        } else {
            return result.get(timeout, TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        try {
            recognizer.stopRecognition();
        } catch (Exception exn) {
            exn.printStackTrace();
            System.out.println(System.currentTimeMillis() + ": exn");
        }
    }
}

